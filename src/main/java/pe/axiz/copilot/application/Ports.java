package pe.axiz.copilot.application;

import java.util.*;
import java.util.function.Consumer;
import pe.axiz.copilot.domain.*;

public final class Ports {
  private Ports() {}

  public interface Store {
    Map<String, Object> payment(String tenant, String payment);

    List<Map<String, Object>> timeline(String tenant, String payment);

    Map<String, Object> merchant(String tenant, String payment);

    List<Map<String, Object>> payments(String tenant);

    List<Map<String, Object>> memory(String tenant, String conversation, int max);

    List<Map<String, Object>> conversations(String tenant);

    void clear(String tenant, String conversation);

    void begin(Investigation command, String runId);

    void finish(Investigation command, String runId, Report report, String narrative);

    Map<String, Object> run(String tenant, String runId);

    void status(String tenant, String runId, String status);

    AutoCloseable lock(String tenant, String conversation);

    boolean bootstrapped(String fingerprint);

    void bootstrap(String fingerprint, int chunks);
  }

  public record ToolCall(String id, String name, String arguments) {}

  public record ModelTurn(String text, List<ToolCall> calls, int inputTokens, int outputTokens) {}

  public interface Model {
    ModelTurn reason(List<Map<String, Object>> messages);

    String structured(List<Map<String, Object>> messages);

    String narrate(String question, Report report, Consumer<String> token);

    List<float[]> embed(List<String> texts);

    String provider();

    String embeddingFingerprint();
  }

  public record Source(String id, String text, String source, double score) {}

  public interface Knowledge {
    default boolean ready() {
      return true;
    }

    List<Source> search(String question, String tenant);

    int ingest(List<Map<String, Object>> documents, String tenant, boolean replace);
  }

  public interface Checkpoints {
    org.bsc.langgraph4j.checkpoint.BaseCheckpointSaver create();
  }

  public interface Events {
    void send(String type, Object data);
  }
}
