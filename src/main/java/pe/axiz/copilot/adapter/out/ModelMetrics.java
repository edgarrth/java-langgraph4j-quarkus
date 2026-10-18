package pe.axiz.copilot.adapter.out;

import dev.langchain4j.model.chat.listener.*;
import io.micrometer.core.instrument.MeterRegistry;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import java.util.concurrent.TimeUnit;

@ApplicationScoped
public class ModelMetrics implements ChatModelListener {
  @Inject MeterRegistry metrics;

  public void onRequest(ChatModelRequestContext context) {
    context.attributes().put("axiz.started", System.nanoTime());
    metrics.counter("copilot.model.requests").increment();
  }

  public void onResponse(ChatModelResponseContext context) {
    var usage = context.chatResponse().tokenUsage();
    if (usage != null) {
      if (usage.inputTokenCount() != null)
        metrics
            .counter("copilot.model.tokens", "direction", "input")
            .increment(usage.inputTokenCount());
      if (usage.outputTokenCount() != null)
        metrics
            .counter("copilot.model.tokens", "direction", "output")
            .increment(usage.outputTokenCount());
    }
    if (context.attributes().get("axiz.started") instanceof Long start)
      metrics
          .timer("copilot.model.duration")
          .record(System.nanoTime() - start, TimeUnit.NANOSECONDS);
  }

  public void onError(ChatModelErrorContext context) {
    metrics.counter("copilot.model.errors").increment();
  }
}
