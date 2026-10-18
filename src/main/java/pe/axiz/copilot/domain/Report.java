package pe.axiz.copilot.domain;

import java.util.*;

public record Report(
    String paymentId,
    String summary,
    Disposition disposition,
    Confidence confidence,
    boolean humanReviewRequired,
    List<Evidence> evidence,
    List<String> recommendedActions,
    List<String> knowledgeSources) {
  public enum Disposition {
    DECLINE_EXPLAINED,
    PAYMENT_HEALTHY,
    PROCESSING_DELAY,
    SETTLEMENT_ISSUE,
    REFUND_OR_REVERSAL_ISSUE,
    INSUFFICIENT_EVIDENCE
  }

  public enum Confidence {
    HIGH,
    MEDIUM,
    LOW
  }

  public record Evidence(String sourceType, String sourceRef, String detail) {}

  public Report {
    if (paymentId == null
        || summary == null
        || summary.isBlank()
        || summary.length() > 12000
        || disposition == null
        || confidence == null
        || evidence == null
        || recommendedActions == null
        || knowledgeSources == null) throw new IllegalArgumentException("Informe incompleto");
    evidence = List.copyOf(evidence);
    recommendedActions = List.copyOf(recommendedActions);
    knowledgeSources = List.copyOf(knowledgeSources);
    if (evidence.size() > 30 || recommendedActions.size() > 12 || knowledgeSources.size() > 20)
      throw new IllegalArgumentException("Informe demasiado extenso");
    for (var e : evidence)
      if (e.sourceType() == null
          || e.sourceRef() == null
          || e.detail() == null
          || e.detail().isBlank()) throw new IllegalArgumentException("Evidencia incompleta");
  }
}
