package pe.axiz.copilot.adapter.out;

import dev.langchain4j.model.chat.request.json.*;
import java.util.*;

public final class ReportSchema {
  private ReportSchema() {}

  public static JsonSchema schema() {
    var evidence =
        JsonObjectSchema.builder()
            .addEnumProperty(
                "sourceType",
                List.of(
                    "PAYMENT_DATABASE", "PAYMENT_TIMELINE", "MERCHANT_PROFILE", "KNOWLEDGE_BASE"))
            .addStringProperty("sourceRef")
            .addStringProperty("detail")
            .required("sourceType", "sourceRef", "detail")
            .additionalProperties(false)
            .build();
    var strings = JsonArraySchema.builder().items(JsonStringSchema.builder().build()).build();
    return JsonSchema.builder()
        .name("PaymentInvestigationReport")
        .rootElement(
            JsonObjectSchema.builder()
                .addStringProperty("paymentId")
                .addStringProperty("summary")
                .addEnumProperty(
                    "disposition",
                    List.of(
                        "DECLINE_EXPLAINED",
                        "PAYMENT_HEALTHY",
                        "PROCESSING_DELAY",
                        "SETTLEMENT_ISSUE",
                        "REFUND_OR_REVERSAL_ISSUE",
                        "INSUFFICIENT_EVIDENCE"))
                .addEnumProperty("confidence", List.of("HIGH", "MEDIUM", "LOW"))
                .addBooleanProperty("humanReviewRequired")
                .addProperty("evidence", JsonArraySchema.builder().items(evidence).build())
                .addProperty("recommendedActions", strings)
                .addProperty("knowledgeSources", strings)
                .required(
                    "paymentId",
                    "summary",
                    "disposition",
                    "confidence",
                    "humanReviewRequired",
                    "evidence",
                    "recommendedActions",
                    "knowledgeSources")
                .additionalProperties(false)
                .build())
        .build();
  }
}
