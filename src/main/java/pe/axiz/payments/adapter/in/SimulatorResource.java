package pe.axiz.payments.adapter.in;

import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import java.util.*;

@Path("/simulator")
@Consumes(MediaType.APPLICATION_JSON)
@Produces(MediaType.APPLICATION_JSON)
public class SimulatorResource {
  @org.eclipse.microprofile.config.inject.ConfigProperty(
      name = "payments.simulator-delay-ms",
      defaultValue = "300")
  int delayMs;

  @POST
  @Path("/{operation}")
  public Map<String, Object> invoke(
      @PathParam("operation") String operation, Map<String, Object> request) {
    try {
      Thread.sleep(delayMs);
    } catch (InterruptedException ex) {
      Thread.currentThread().interrupt();
      throw new IllegalStateException("Simulación interrumpida", ex);
    }
    var scenario = String.valueOf(request.get("scenario"));
    return switch (operation) {
      case "risk" ->
          Map.of(
              "risk",
              scenario.equals("DECLINE") ? "HIGH" : scenario.equals("REVIEW") ? "MEDIUM" : "LOW");
      case "merchant" -> Map.of("merchantActive", true);
      case "authorize" ->
          Map.of(
              "status",
              scenario.equals("FAILOVER") && ((Number) request.get("attempt")).intValue() == 1
                  ? "UNAVAILABLE"
                  : "AUTHORIZED",
              "acquirer",
              ((Number) request.get("attempt")).intValue() == 1 ? "primary" : "secondary",
              "authorizationId",
              "auth-" + request.get("id"));
      case "capture" ->
          Map.of("status", scenario.equals("COMPENSATE") ? "CAPTURE_FAILED" : "CAPTURED");
      case "void" -> Map.of("status", "COMPENSATED");
      default -> throw new NotFoundException("Operación inexistente");
    };
  }
}
