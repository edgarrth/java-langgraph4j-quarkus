package pe.axiz.copilot.application;

import static pe.axiz.copilot.application.CopilotException.Reason.*;

import io.micrometer.core.instrument.MeterRegistry;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.Executors;
import org.bsc.langgraph4j.*;
import pe.axiz.copilot.domain.*;

@ApplicationScoped
public class CopilotService {
  @Inject Ports.Store store;
  @Inject Ports.Model model;
  @Inject Ports.Knowledge knowledge;

  @org.eclipse.microprofile.config.inject.ConfigProperty(name = "copilot.memory-window")
  int memoryWindow;

  @Inject Ports.Checkpoints checkpoints;
  @Inject MeterRegistry metrics;

  private CompiledGraph<WorkflowState> graph(Ports.Events events, boolean stream) throws Exception {
    var saver = checkpoints.create();
    return new CopilotWorkflow(model, knowledge, store, memoryWindow)
        .compile(saver, events, stream);
  }

  public static String runId(Investigation c) {
    return UUID.nameUUIDFromBytes(
            (c.tenantId() + ":" + c.conversationId() + ":" + c.requestId())
                .getBytes(StandardCharsets.UTF_8))
        .toString();
  }

  public Map<String, Object> investigate(
      Investigation command, Ports.Events events, boolean streaming) {
    Guardrails.input(command.question());
    String id = runId(command);
    try (var lease = store.lock(command.tenantId(), command.conversationId())) {
      store.begin(command, id);
      var run = store.run(command.tenantId(), id);
      if ("COMPLETED".equals(run.get("status"))) {
        var result = result(command.tenantId(), id);
        events.send("result", result);
        return result;
      }
      var graph = graph(events, streaming);
      var cfg = RunnableConfig.builder().threadId(id).build();
      var previous = graph.stateOf(cfg);
      if (previous.isPresent()) {
        if (previous.get().nextNodeId().equals("human_review")) {
          var result = result(command.tenantId(), id);
          events.send("result", result);
          return result;
        }
        throw new CopilotException(
            CONFLICT,
            "La investigación tiene un checkpoint pendiente; se debe utilizar la recuperación");
      }
      return execute(
          command,
          graph,
          GraphInput.args(Map.of("command", Json.map(command), "runId", id, "status", "RUNNING")),
          events);
    } catch (CopilotException ex) {
      throw ex;
    } catch (Exception ex) {
      throw translate(ex);
    }
  }

  public Map<String, Object> resume(
      String tenant, String id, String decision, Ports.Events events, boolean streaming) {
    var row = store.run(tenant, id);
    var command = command(tenant, row);
    if (!Set.of("APPROVED", "REJECTED").contains(decision))
      throw new CopilotException(INVALID, "Se requiere APPROVED o REJECTED");
    try (var lease = store.lock(tenant, command.conversationId())) {
      var graph = graph(events, streaming);
      var s =
          graph
              .stateOf(RunnableConfig.builder().threadId(id).build())
              .orElseThrow(() -> new CopilotException(NOT_FOUND, "No existe checkpoint"));
      if (!s.nextNodeId().equals("human_review"))
        throw new CopilotException(CONFLICT, "La investigación no espera revisión");
      return execute(command, graph, GraphInput.resume(Map.of("reviewDecision", decision)), events);
    } catch (CopilotException ex) {
      throw ex;
    } catch (Exception ex) {
      throw translate(ex);
    }
  }

  public Map<String, Object> retry(
      String tenant, String id, Ports.Events events, boolean streaming) {
    var row = store.run(tenant, id);
    var command = command(tenant, row);
    try (var lease = store.lock(tenant, command.conversationId())) {
      var graph = graph(events, streaming);
      var s =
          graph
              .stateOf(RunnableConfig.builder().threadId(id).build())
              .orElseThrow(
                  () ->
                      new CopilotException(
                          CONFLICT,
                          "No existe checkpoint para recuperar; se requiere un requestId nuevo"));
      if (s.nextNodeId().equals("human_review") || s.nextNodeId().equals("__END__"))
        throw new CopilotException(CONFLICT, "No existe un nodo fallido pendiente");
      return execute(command, graph, GraphInput.resume(), events);
    } catch (CopilotException ex) {
      throw ex;
    } catch (Exception ex) {
      throw translate(ex);
    }
  }

  private Map<String, Object> execute(
      Investigation cmd,
      CompiledGraph<WorkflowState> graph,
      GraphInput input,
      Ports.Events events) {
    String id = runId(cmd);
    long start = System.nanoTime();
    metrics.counter("copilot.runs", "provider", model.provider()).increment();
    events.send(
        "run",
        Map.of("runId", id, "conversationId", cmd.conversationId(), "provider", model.provider()));
    try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
      var cfg =
          RunnableConfig.builder()
              .threadId(id)
              .addParallelNodeExecutor("context", executor)
              .build();
      for (var output : graph.stream(input, cfg)) {
        events.send(
            "node",
            Map.of("runId", id, "node", output.node(), "status", output.state().text("status")));
      }
      var s = graph.getState(cfg);
      if (s.nextNodeId().equals("human_review"))
        store.status(cmd.tenantId(), id, "AWAITING_REVIEW");
      var result = result(cmd.tenantId(), id);
      events.send("result", result);
      return result;
    } catch (RuntimeException ex) {
      if (!"COMPLETED".equals(store.run(cmd.tenantId(), id).get("status"))) {
        boolean waiting = false;
        try {
          waiting =
              graph
                  .stateOf(RunnableConfig.builder().threadId(id).build())
                  .map(last -> last.nextNodeId().equals("human_review"))
                  .orElse(false);
        } catch (RuntimeException ignored) {
        }
        store.status(cmd.tenantId(), id, waiting ? "AWAITING_REVIEW" : "FAILED");
      }
      metrics.counter("copilot.failures", "provider", model.provider()).increment();
      throw translate(ex);
    } finally {
      metrics
          .timer("copilot.duration", "provider", model.provider())
          .record(System.nanoTime() - start, java.util.concurrent.TimeUnit.NANOSECONDS);
    }
  }

  private CopilotException translate(Throwable error) {

    for (Throwable c = error; c != null; c = c.getCause())
      if (c instanceof CopilotException ex) return ex;
    return new CopilotException(
        UNAVAILABLE,
        "No se pudo completar la investigación. Se conserva el checkpoint para recuperación; no se ejecutó una operación financiera.");
  }

  public Map<String, Object> result(String tenant, String id) {
    var run = new HashMap<>(store.run(tenant, id));
    run.remove("question");
    try {
      var graph = graph((type, data) -> {}, false);
      var s = graph.stateOf(RunnableConfig.builder().threadId(id).build());
      if (s.isPresent()) {
        run.put("nextNode", s.get().nextNodeId());
        run.put("checkpointId", s.get().checkpointId());
        if (s.get().state().data().containsKey("report"))
          run.put("report", s.get().state().data().get("report"));
      }
    } catch (Exception ex) {
      throw translate(ex);
    }
    if (run.get("report") instanceof String text && !text.isBlank())
      run.put("report", Json.object(text));
    run.put("provider", model.provider());
    return run;
  }

  public List<Map<String, Object>> checkpoints(String tenant, String id) {
    store.run(tenant, id);
    try {
      var graph = graph((type, data) -> {}, false);
      return graph.getStateHistory(RunnableConfig.builder().threadId(id).build()).stream()
          .map(
              s ->
                  Map.<String, Object>of(
                      "checkpointId",
                      s.checkpointId(),
                      "node",
                      s.node(),
                      "nextNode",
                      s.nextNodeId(),
                      "status",
                      s.state().text("status"),
                      "reportAttempts",
                      s.state().number("reportAttempts"),
                      "toolRounds",
                      s.state().number("rounds")))
          .toList();
    } catch (Exception ex) {
      throw translate(ex);
    }
  }

  public Map<String, Object> checkpoint(String tenant, String id, String checkpointId) {
    return checkpoints(tenant, id).stream()
        .filter(c -> c.get("checkpointId").equals(checkpointId))
        .findFirst()
        .orElseThrow(() -> new CopilotException(NOT_FOUND, "Checkpoint inexistente"));
  }

  public void clear(String tenant, String conversation) {
    try (var lease = store.lock(tenant, conversation)) {
      store.clear(tenant, conversation);
    } catch (CopilotException ex) {
      throw ex;
    } catch (Exception ex) {
      throw translate(ex);
    }
  }

  public String diagram() {
    try {
      return graph((type, data) -> {}, false).getGraph(GraphRepresentation.Type.MERMAID).content();
    } catch (Exception ex) {
      throw translate(ex);
    }
  }

  private Investigation command(String tenant, Map<String, Object> row) {
    return new Investigation(
        tenant,
        row.get("payment_id").toString(),
        row.get("conversation_id").toString(),
        row.get("request_id").toString(),
        row.get("question").toString(),
        Boolean.TRUE.equals(row.get("review_requested")));
  }
}
