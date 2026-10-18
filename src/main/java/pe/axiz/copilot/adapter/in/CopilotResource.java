package pe.axiz.copilot.adapter.in;

import jakarta.inject.Inject;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.*;
import jakarta.ws.rs.sse.*;
import java.util.*;
import pe.axiz.copilot.adapter.out.CopilotConfig;
import pe.axiz.copilot.application.*;
import pe.axiz.copilot.domain.*;

@Path("/api/v1")
@Consumes(MediaType.APPLICATION_JSON)
@Produces(MediaType.APPLICATION_JSON)
public class CopilotResource {
  @Inject CopilotService service;
  @Inject Ports.Store store;
  @Inject Ports.Model model;
  @Inject Ports.Knowledge knowledge;
  @Inject CopilotConfig config;

  public record Request(
      @NotBlank @Pattern(regexp = "[a-zA-Z0-9_-]{1,64}") String conversationId,
      @NotBlank @Pattern(regexp = "[a-zA-Z0-9_-]{1,64}") String requestId,
      @NotBlank @Size(max = 4000) String question,
      boolean reviewRequested) {}

  public record Decision(@NotBlank @Pattern(regexp = "APPROVED|REJECTED") String decision) {}

  public record Ingestion(@NotEmpty @Size(max = 40) List<Map<String, Object>> documents) {}

  private Investigation command(String tenant, String payment, Request request) {
    try {
      return new Investigation(
          tenant,
          payment,
          request.conversationId(),
          request.requestId(),
          request.question(),
          request.reviewRequested());
    } catch (IllegalArgumentException ex) {
      throw new CopilotException(CopilotException.Reason.INVALID, ex.getMessage());
    }
  }

  @GET
  @Path("/runtime")
  public Map<String, Object> runtime() {
    return Map.of(
        "provider",
        model.provider(),
        "chatModel",
        model.provider().equals("FIXTURE") ? "Sin modelo generativo" : config.chatModel(),
        "version",
        "2.0.0",
        "readOnlyTools",
        true);
  }

  @GET
  @Path("/payments")
  public List<Map<String, Object>> payments(@HeaderParam("X-Tenant-Id") String tenant) {
    return store.payments(tenant);
  }

  @GET
  @Path("/payments/{paymentId}")
  public Map<String, Object> payment(
      @HeaderParam("X-Tenant-Id") String tenant, @PathParam("paymentId") String payment) {
    return store.payment(tenant, payment);
  }

  @GET
  @Path("/payments/{paymentId}/timeline")
  public List<Map<String, Object>> timeline(
      @HeaderParam("X-Tenant-Id") String tenant, @PathParam("paymentId") String payment) {
    return store.timeline(tenant, payment);
  }

  @POST
  @Path("/payments/{paymentId}/investigations")
  public Map<String, Object> investigate(
      @HeaderParam("X-Tenant-Id") String tenant,
      @PathParam("paymentId") String payment,
      @Valid Request request) {
    return service.investigate(command(tenant, payment, request), (type, data) -> {}, false);
  }

  @POST
  @Path("/payments/{paymentId}/investigations/stream")
  @Produces(MediaType.SERVER_SENT_EVENTS)
  public void stream(
      @HeaderParam("X-Tenant-Id") String tenant,
      @PathParam("paymentId") String payment,
      @Valid Request request,
      @Context SseEventSink sink,
      @Context Sse sse) {
    var cmd = command(tenant, payment, request);
    Guardrails.input(cmd.question());
    store.payment(tenant, payment);
    stream(sink, sse, events -> service.investigate(cmd, events, true));
  }

  @GET
  @Path("/conversations")
  public List<Map<String, Object>> conversations(@HeaderParam("X-Tenant-Id") String tenant) {
    return store.conversations(tenant);
  }

  @GET
  @Path("/conversations/{id}/messages")
  public List<Map<String, Object>> memory(
      @HeaderParam("X-Tenant-Id") String tenant, @PathParam("id") String id) {
    return store.memory(tenant, id, 200);
  }

  @DELETE
  @Path("/conversations/{id}/messages")
  public Map<String, Object> clear(
      @HeaderParam("X-Tenant-Id") String tenant, @PathParam("id") String id) {
    service.clear(tenant, id);
    return Map.of("cleared", true);
  }

  @GET
  @Path("/runs/{id}")
  public Map<String, Object> run(
      @HeaderParam("X-Tenant-Id") String tenant, @PathParam("id") String id) {
    return service.result(tenant, id);
  }

  @GET
  @Path("/runs/{id}/checkpoints")
  public List<Map<String, Object>> history(
      @HeaderParam("X-Tenant-Id") String tenant, @PathParam("id") String id) {
    return service.checkpoints(tenant, id);
  }

  @GET
  @Path("/runs/{id}/checkpoints/{checkpoint}")
  public Map<String, Object> checkpoint(
      @HeaderParam("X-Tenant-Id") String tenant,
      @PathParam("id") String id,
      @PathParam("checkpoint") String checkpoint) {
    return service.checkpoint(tenant, id, checkpoint);
  }

  @POST
  @Path("/runs/{id}/review")
  public Map<String, Object> review(
      @HeaderParam("X-Tenant-Id") String tenant,
      @HeaderParam("X-Reviewer-Key") String reviewer,
      @PathParam("id") String id,
      @Valid Decision decision) {
    reviewer(reviewer);
    return service.resume(tenant, id, decision.decision(), (type, data) -> {}, false);
  }

  @POST
  @Path("/runs/{id}/review/stream")
  @Produces(MediaType.SERVER_SENT_EVENTS)
  public void reviewStream(
      @HeaderParam("X-Tenant-Id") String tenant,
      @HeaderParam("X-Reviewer-Key") String reviewer,
      @PathParam("id") String id,
      @Valid Decision decision,
      @Context SseEventSink sink,
      @Context Sse sse) {
    reviewer(reviewer);
    store.run(tenant, id);
    stream(sink, sse, events -> service.resume(tenant, id, decision.decision(), events, true));
  }

  @POST
  @Path("/runs/{id}/retry/stream")
  @Produces(MediaType.SERVER_SENT_EVENTS)
  public void retry(
      @HeaderParam("X-Tenant-Id") String tenant,
      @PathParam("id") String id,
      @Context SseEventSink sink,
      @Context Sse sse) {
    store.run(tenant, id);
    stream(sink, sse, events -> service.retry(tenant, id, events, true));
  }

  @POST
  @Path("/knowledge/documents")
  public Map<String, Object> ingest(
      @HeaderParam("X-Tenant-Id") String tenant,
      @HeaderParam("X-Reviewer-Key") String reviewer,
      @Valid Ingestion data) {
    reviewer(reviewer);
    try {
      return Map.of("chunks", knowledge.ingest(data.documents(), tenant, false));
    } catch (IllegalArgumentException ex) {
      throw new CopilotException(CopilotException.Reason.INVALID, ex.getMessage());
    }
  }

  @GET
  @Path("/graph")
  @Produces(MediaType.TEXT_PLAIN)
  public String graph() {
    return service.diagram();
  }

  private void reviewer(String key) {
    if (!CopilotSecurity.constantEquals(config.reviewerKey(), key))
      throw new CopilotException(
          CopilotException.Reason.FORBIDDEN, "Se requiere una credencial de revisor válida");
  }

  private void stream(
      SseEventSink sink, Sse sse, java.util.function.Function<Ports.Events, Object> action) {
    try (sink) {
      var lock = new Object();
      Ports.Events events =
          (type, data) -> {
            synchronized (lock) {
              if (sink.isClosed())
                throw new CopilotException(
                    CopilotException.Reason.UNAVAILABLE, "Se cerró el seguimiento");
              sink.send(
                      sse.newEventBuilder()
                          .name(type)
                          .mediaType(MediaType.APPLICATION_JSON_TYPE)
                          .data(Object.class, data)
                          .build())
                  .toCompletableFuture()
                  .join();
            }
          };
      try {
        action.apply(events);
      } catch (RuntimeException error) {
        if (!sink.isClosed()) {
          String message =
              error instanceof CopilotException
                  ? error.getMessage()
                  : "La ejecución se interrumpió; se debe consultar su checkpoint antes de reintentar.";
          events.send(
              "error",
              Map.of(
                  "message",
                  message,
                  "recoverable",
                  !(error instanceof CopilotException ex)
                      || ex.reason() == CopilotException.Reason.UNAVAILABLE));
        }
      }
    }
  }
}
