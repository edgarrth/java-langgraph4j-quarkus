package pe.axiz.payments.application;

import static org.bsc.langgraph4j.StateGraph.*;
import static org.bsc.langgraph4j.action.AsyncEdgeAction.edge_async;
import static org.bsc.langgraph4j.action.AsyncNodeAction.node_async;

import jakarta.annotation.PostConstruct;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import java.nio.file.Path;
import java.util.*;
import org.bsc.langgraph4j.*;
import org.bsc.langgraph4j.checkpoint.FileSystemSaver;
import org.bsc.langgraph4j.serializer.std.ObjectStreamStateSerializer;
import org.bsc.langgraph4j.state.Channels;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import pe.axiz.payments.domain.Payment;

@ApplicationScoped
public class PaymentGraph {
  @Inject PaymentServices services;

  @ConfigProperty(name = "payments.checkpoints")
  String folder;

  private CompiledGraph<PaymentState> graph;
  private final java.util.concurrent.ExecutorService parallelExecutor =
      java.util.concurrent.Executors.newVirtualThreadPerTaskExecutor();

  @jakarta.annotation.PreDestroy
  void close() {
    parallelExecutor.close();
  }

  @PostConstruct
  void init() {
    try {
      var channels =
          Map.<String, org.bsc.langgraph4j.state.Channel<?>>of(
              "events", Channels.<String>appender(ArrayList::new));
      var settlement =
          new StateGraph<PaymentState>(channels, PaymentState::new)
              .addNode(
                  "capture", node_async(s -> merge(services.call("capture", s.data()), "capture")))
              .addNode(
                  "compensate",
                  node_async(s -> merge(services.call("void", s.data()), "compensate")))
              .addEdge(START, "capture")
              .addConditionalEdges(
                  "capture",
                  edge_async(s -> s.text("status").equals("CAPTURE_FAILED") ? "void" : "end"),
                  Map.of("void", "compensate", "end", END))
              .addEdge("compensate", END);
      var definition =
          new StateGraph<PaymentState>(channels, PaymentState::new)
              .addNode("validate", node_async(s -> event("VALIDATED", "validate")))
              .addNode("risk", node_async(s -> merge(services.call("risk", s.data()), "risk")))
              .addNode(
                  "merchant",
                  node_async(s -> merge(services.call("merchant", s.data()), "merchant")))
              .addNode("decision", node_async(s -> event("DECIDED", "decision")))
              .addNode("review", node_async(s -> event("AWAITING_APPROVAL", "review")))
              .addNode("approval", node_async(s -> Map.of("events", List.of("approval"))))
              .addNode(
                  "authorize",
                  node_async(
                      s -> {
                        var input = new HashMap<>(s.data());
                        input.put("attempt", s.attempt() + 1);
                        var output = merge(services.call("authorize", input), "authorize");
                        output.put("attempt", s.attempt() + 1);
                        return output;
                      }))
              .addNode("settlement", settlement)
              .addNode("reject", node_async(s -> event("REJECTED", "reject")))
              .addEdge(START, "validate")
              .addEdge("validate", "risk")
              .addEdge("validate", "merchant")
              .addEdge("risk", "decision")
              .addEdge("merchant", "decision")
              .addConditionalEdges(
                  "decision",
                  edge_async(
                      s ->
                          s.text("risk").equals("HIGH")
                              ? "reject"
                              : s.text("risk").equals("MEDIUM") ? "review" : "pay"),
                  Map.of("reject", "reject", "review", "review", "pay", "authorize"))
              .addEdge("review", "approval")
              .addConditionalEdges(
                  "approval",
                  edge_async(s -> s.text("approval").equals("APPROVED") ? "pay" : "reject"),
                  Map.of("pay", "authorize", "reject", "reject"))
              .addConditionalEdges(
                  "authorize",
                  edge_async(
                      s ->
                          s.text("status").equals("AUTHORIZED")
                              ? "capture"
                              : s.attempt() < 2 ? "retry" : "reject"),
                  Map.of("capture", "settlement", "retry", "authorize", "reject", "reject"))
              .addEdge("settlement", END)
              .addEdge("reject", END);
      graph =
          definition.compile(
              CompileConfig.builder()
                  .checkpointSaver(
                      new FileSystemSaver(
                          Path.of(folder), new ObjectStreamStateSerializer<>(PaymentState::new)))
                  .interruptBefore("approval")
                  .releaseThread(false)
                  .recursionLimit(30)
                  .build());
    } catch (Exception ex) {
      throw new IllegalStateException("No se pudo compilar el grafo", ex);
    }
  }

  private static Map<String, Object> event(String status, String node) {
    return new HashMap<>(Map.of("status", status, "events", List.of(node)));
  }

  private static Map<String, Object> merge(Map<String, Object> data, String node) {
    var result = new HashMap<>(data);
    result.put("events", List.of(node));
    return result;
  }

  private RunnableConfig config(String id) {
    return RunnableConfig.builder()
        .threadId(id)
        .addParallelNodeExecutor("validate", parallelExecutor)
        .build();
  }

  public synchronized Map<String, Object> start(Payment payment) {
    var existing = graph.stateOf(config(payment.id()));
    if (existing.isPresent()) {
      var data = existing.get().state().data();
      if (!Objects.equals(data.get("scenario"), payment.scenario())
          || ((Number) data.get("amountMinor")).longValue() != payment.amountMinor()
          || !Objects.equals(data.get("currency"), payment.currency()))
        throw new jakarta.ws.rs.ClientErrorException(
            "El identificador ya corresponde a otra solicitud", 409);
      return state(payment.id());
    }
    return execute(payment.id(), GraphInput.args(payment.state()));
  }

  private Map<String, Object> execute(String id, GraphInput input) {
    return execute(id, input, step -> {});
  }

  private Map<String, Object> execute(
      String id, GraphInput input, java.util.function.Consumer<Map<String, Object>> listener) {
    var outputs = new ArrayList<Map<String, Object>>();
    for (var output : graph.stream(input, config(id))) {
      var step = Map.<String, Object>of("node", output.node(), "state", output.state().data());
      outputs.add(step);
      listener.accept(step);
    }
    var result = new HashMap<>(state(id));
    result.put("steps", outputs);
    return result;
  }

  public synchronized Map<String, Object> resume(String id, String approval) {
    var snapshot =
        graph
            .stateOf(config(id))
            .orElseThrow(() -> new jakarta.ws.rs.NotFoundException("Pago inexistente"));
    if (!snapshot.nextNodeId().equals("approval"))
      throw new jakarta.ws.rs.ClientErrorException("El pago no espera aprobación", 409);
    if (!Set.of("APPROVED", "REJECTED").contains(approval))
      throw new jakarta.ws.rs.BadRequestException("Decisión inválida");
    return execute(id, GraphInput.resume(Map.of("approval", approval)));
  }

  public synchronized Map<String, Object> state(String id) {
    var s =
        graph
            .stateOf(config(id))
            .orElseThrow(() -> new jakarta.ws.rs.NotFoundException("Pago inexistente"));
    return Map.of(
        "checkpointId", s.checkpointId(), "nextNode", s.nextNodeId(), "state", s.state().data());
  }

  public synchronized List<Map<String, Object>> history(String id) {
    state(id);
    return graph.getStateHistory(config(id)).stream()
        .map(
            s ->
                Map.<String, Object>of(
                    "checkpointId",
                    s.checkpointId(),
                    "node",
                    s.node(),
                    "nextNode",
                    s.nextNodeId(),
                    "state",
                    s.state().data()))
        .toList();
  }

  public synchronized Map<String, Object> retry(String id) {
    var s = state(id);
    if (Set.of(END, "approval").contains(s.get("nextNode")))
      throw new jakarta.ws.rs.ClientErrorException("No existe un nodo fallido pendiente", 409);
    return execute(id, GraphInput.resume());
  }

  public synchronized Map<String, Object> checkpoint(String id, String checkpointId) {
    state(id);
    return graph.getStateHistory(config(id)).stream()
        .filter(s -> s.checkpointId().equals(checkpointId))
        .findFirst()
        .map(
            s ->
                Map.<String, Object>of(
                    "node", s.node(), "nextNode", s.nextNodeId(), "state", s.state().data()))
        .orElseThrow(() -> new jakarta.ws.rs.NotFoundException("Checkpoint inexistente"));
  }

  public synchronized Map<String, Object> stream(
      Payment payment, java.util.function.Consumer<Map<String, Object>> listener) {
    if (graph.stateOf(config(payment.id())).isPresent()) {
      var result = start(payment);
      return result;
    }
    return execute(payment.id(), GraphInput.args(payment.state()), listener);
  }

  public String diagram() {
    return graph.getGraph(GraphRepresentation.Type.MERMAID).content();
  }
}
