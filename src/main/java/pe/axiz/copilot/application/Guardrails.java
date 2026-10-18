package pe.axiz.copilot.application;

import static pe.axiz.copilot.application.CopilotException.Reason.*;

import java.text.Normalizer;
import java.util.*;
import pe.axiz.copilot.domain.*;

public final class Guardrails {
  private Guardrails() {}

  public static void input(String text) {
    var normalized = Normalizer.normalize(text, Normalizer.Form.NFKC).toLowerCase(Locale.ROOT);
    for (String blocked :
        List.of(
            "ignore previous instructions",
            "reveal system prompt",
            "show your system prompt",
            "print api key",
            "reveal api key",
            "ignora las instrucciones",
            "revela el prompt",
            "muestra la clave api"))
      if (normalized.contains(blocked))
        throw new CopilotException(INVALID, "La solicitud incumple la política de entrada");
  }

  public static void report(
      Report report, String paymentId, Set<String> allowed, List<String> knowledge) {
    if (!report.paymentId().equals(paymentId))
      throw new IllegalArgumentException("El informe corresponde a otro pago");
    if (report.evidence().isEmpty())
      throw new IllegalArgumentException("Se requiere evidencia verificable");
    for (var e : report.evidence()) {
      if (!Set.of("PAYMENT_DATABASE", "PAYMENT_TIMELINE", "MERCHANT_PROFILE", "KNOWLEDGE_BASE")
              .contains(e.sourceType())
          || !allowed.contains(e.sourceRef()))
        throw new IllegalArgumentException("Referencia de evidencia no autorizada");
      boolean typeMatches =
          switch (e.sourceType()) {
            case "PAYMENT_DATABASE" -> e.sourceRef().equals("PAYMENT:" + paymentId);
            case "PAYMENT_TIMELINE" -> e.sourceRef().equals("TIMELINE:" + paymentId);
            case "MERCHANT_PROFILE" -> e.sourceRef().equals("MERCHANT:" + paymentId);
            case "KNOWLEDGE_BASE" -> knowledge.contains(e.sourceRef());
            default -> false;
          };
      if (!typeMatches)
        throw new IllegalArgumentException("El tipo de evidencia no coincide con su fuente");
    }
    if (!knowledge.containsAll(report.knowledgeSources()))
      throw new IllegalArgumentException("Fuente de conocimiento no recuperada");
  }
}
