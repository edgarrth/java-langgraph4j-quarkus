package pe.axiz.payments.adapter.in;

import jakarta.inject.Inject;
import jakarta.validation.Valid;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import java.util.*;
import pe.axiz.payments.application.PaymentGraph;
import pe.axiz.payments.domain.Payment;

@Path("/payments")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class PaymentResource {
  @Inject PaymentGraph graph;

  public record Decision(String approval) {}

  @POST
  public Map<String, Object> start(@Valid Payment payment) {
    return graph.start(payment);
  }

  @GET
  @Path("/{id}")
  public Map<String, Object> state(@PathParam("id") String id) {
    return graph.state(validId(id));
  }

  @GET
  @Path("/{id}/history")
  public List<Map<String, Object>> history(@PathParam("id") String id) {
    return graph.history(validId(id));
  }

  @POST
  @Path("/{id}/resume")
  public Map<String, Object> resume(@PathParam("id") String id, Decision decision) {
    return graph.resume(validId(id), decision.approval());
  }

  @POST
  @Path("/{id}/retry")
  public Map<String, Object> retry(@PathParam("id") String id) {
    return graph.retry(validId(id));
  }

  @GET
  @Path("/graph")
  @Produces(MediaType.TEXT_PLAIN)
  public String diagram() {
    return graph.diagram();
  }

  @GET
  @Path("/{id}/checkpoints/{checkpointId}")
  public Map<String, Object> checkpoint(
      @PathParam("id") String id, @PathParam("checkpointId") String checkpointId) {
    return graph.checkpoint(validId(id), checkpointId);
  }

  @POST
  @Path("/stream")
  @Produces(MediaType.SERVER_SENT_EVENTS)
  public void stream(
      @Valid Payment payment,
      @jakarta.ws.rs.core.Context jakarta.ws.rs.sse.SseEventSink sink,
      @jakarta.ws.rs.core.Context jakarta.ws.rs.sse.Sse sse) {
    try (sink) {
      var result =
          graph.stream(
              payment,
              step ->
                  sink.send(
                          sse.newEventBuilder()
                              .name("node")
                              .mediaType(MediaType.APPLICATION_JSON_TYPE)
                              .data(Map.class, step)
                              .build())
                      .toCompletableFuture()
                      .join());
      sink.send(
              sse.newEventBuilder()
                  .name("result")
                  .mediaType(MediaType.APPLICATION_JSON_TYPE)
                  .data(Map.class, result)
                  .build())
          .toCompletableFuture()
          .join();
    }
  }

  private String validId(String id) {
    if (!id.matches("[a-zA-Z0-9-]{1,64}")) throw new BadRequestException("Identificador inválido");
    return id;
  }
}
