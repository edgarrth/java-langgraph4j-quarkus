package pe.axiz.copilot.application;

import static pe.axiz.copilot.application.CopilotException.Reason.*;

import java.util.*;
import pe.axiz.copilot.domain.Investigation;

public final class Tools {
  public static final Set<String> NAMES =
      Set.of("getPayment", "getPaymentTimeline", "getPaymentMerchant");
  private final Ports.Store store;

  public Tools(Ports.Store store) {
    this.store = store;
  }

  public Map<String, Object> execute(
      Investigation scope, Ports.ToolCall call, Map<String, Integer> counts) {
    if (!NAMES.contains(call.name()))
      throw new CopilotException(FORBIDDEN, "Herramienta no autorizada");
    var args = Json.object(call.arguments());
    if (!args.keySet().equals(Set.of("paymentId"))
        || !scope.paymentId().equals(args.get("paymentId")))
      throw new CopilotException(FORBIDDEN, "La herramienta intentó salir del ámbito autorizado");
    if (counts.values().stream().mapToInt(Integer::intValue).sum() >= 8
        || counts.getOrDefault(call.name(), 0) >= 4)
      throw new CopilotException(LIMIT, "Se alcanzó el límite de herramientas del turno");
    counts.merge(call.name(), 1, Integer::sum);
    return switch (call.name()) {
      case "getPayment" ->
          Map.of(
              "sourceRef",
              "PAYMENT:" + scope.paymentId(),
              "data",
              store.payment(scope.tenantId(), scope.paymentId()));
      case "getPaymentTimeline" ->
          Map.of(
              "sourceRef",
              "TIMELINE:" + scope.paymentId(),
              "data",
              store.timeline(scope.tenantId(), scope.paymentId()));
      case "getPaymentMerchant" ->
          Map.of(
              "sourceRef",
              "MERCHANT:" + scope.paymentId(),
              "data",
              store.merchant(scope.tenantId(), scope.paymentId()));
      default -> throw new CopilotException(FORBIDDEN, "Herramienta no autorizada");
    };
  }
}
