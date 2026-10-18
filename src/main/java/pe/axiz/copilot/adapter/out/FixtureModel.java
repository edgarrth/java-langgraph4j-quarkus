package pe.axiz.copilot.adapter.out;

import java.util.*;
import java.util.function.Consumer;
import java.util.regex.Pattern;
import pe.axiz.copilot.application.*;
import pe.axiz.copilot.domain.*;

public class FixtureModel implements Ports.Model {
  public String provider() {
    return "FIXTURE";
  }

  public String embeddingFingerprint() {
    return "fixture-hash-1536";
  }

  public Ports.ModelTurn reason(List<Map<String, Object>> messages) {
    var user =
        messages.stream()
            .filter(m -> m.get("role").equals("user"))
            .reduce((a, b) -> b)
            .orElseThrow();
    var match =
        Pattern.compile("Pago autorizado: ([a-zA-Z0-9_-]+)")
            .matcher(user.get("content").toString());
    if (!match.find()) throw new IllegalArgumentException("Solicitud de fixture sin pago");
    var id = match.group(1);
    boolean tools = messages.stream().anyMatch(m -> m.get("role").equals("tool"));
    return new Ports.ModelTurn(
        tools ? "Se dispone de evidencia para el informe." : "",
        tools
            ? List.of()
            : List.of(
                new Ports.ToolCall(
                    "fixture-payment", "getPayment", Json.write(Map.of("paymentId", id))),
                new Ports.ToolCall(
                    "fixture-timeline", "getPaymentTimeline", Json.write(Map.of("paymentId", id))),
                new Ports.ToolCall(
                    "fixture-merchant", "getPaymentMerchant", Json.write(Map.of("paymentId", id)))),
        0,
        0);
  }

  public String structured(List<Map<String, Object>> messages) {
    var all = messages.toString();
    String id =
        List.of("pay_1001", "pay_1002", "pay_1003", "pay_1004").stream()
            .filter(all::contains)
            .findFirst()
            .orElse("pay_unknown");
    boolean timeout = id.equals("pay_1003"), decline = id.equals("pay_1002");
    var summary =
        timeout
            ? "La captura tiene resultado incierto por timeout. Se debe conciliar antes de reintentar para evitar duplicados."
            : decline
                ? "El emisor rechazó la autorización con DO_NOT_HONOR, sin indicar una causa específica. No se puede concluir que falten fondos."
                : "La evidencia operacional indica un pago capturado o liquidado; se debe distinguir captura de liquidación.";
    return Json.write(
        new Report(
            id,
            summary,
            timeout
                ? Report.Disposition.PROCESSING_DELAY
                : decline
                    ? Report.Disposition.DECLINE_EXPLAINED
                    : Report.Disposition.PAYMENT_HEALTHY,
            Report.Confidence.HIGH,
            timeout,
            List.of(
                new Report.Evidence(
                    "PAYMENT_DATABASE", "PAYMENT:" + id, "Se consultó el estado actual del pago."),
                new Report.Evidence(
                    "PAYMENT_TIMELINE",
                    "TIMELINE:" + id,
                    "Se consultaron los eventos operacionales.")),
            List.of(
                timeout
                    ? "Consultar el resultado del procesador antes de repetir la captura."
                    : "Conservar la evidencia operacional y seguir el playbook."),
            List.of()));
  }

  public String narrate(String question, Report report, Consumer<String> token) {
    var text = "[Modo de prueba sin LLM] " + report.summary();
    for (String word : text.split("(?<= )")) token.accept(word);
    return text;
  }

  public List<float[]> embed(List<String> texts) {
    var out = new ArrayList<float[]>();
    for (var text : texts) {
      var v = new float[1536];
      for (String term : text.toLowerCase(Locale.ROOT).split("\\W+")) {
        int i = Math.floorMod(term.hashCode(), v.length);
        v[i] += 1;
      }
      double norm = 0;
      for (float f : v) norm += f * f;
      double denom = Math.sqrt(norm);
      if (denom > 0) for (int i = 0; i < v.length; i++) v[i] /= (float) denom;
      out.add(v);
    }
    return out;
  }
}
