package pe.axiz.copilot.domain;

import java.util.*;

public record Investigation(
    String tenantId,
    String paymentId,
    String conversationId,
    String requestId,
    String question,
    boolean reviewRequested) {
  public Investigation {
    for (String id : List.of(tenantId, paymentId, conversationId, requestId))
      if (!id.matches("[a-zA-Z0-9_-]{1,64}"))
        throw new IllegalArgumentException("Identificador inválido");
    if (question == null || question.isBlank() || question.length() > 4000)
      throw new IllegalArgumentException("Se requiere una pregunta de hasta 4000 caracteres");
  }
}
