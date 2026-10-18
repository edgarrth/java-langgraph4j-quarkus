package pe.axiz.copilot.adapter.out;

import io.smallrye.config.ConfigMapping;
import io.smallrye.config.WithDefault;

@ConfigMapping(prefix = "copilot")
public interface CopilotConfig {
  @WithDefault("OPENAI")
  String provider();

  @WithDefault("")
  java.util.Optional<String> openaiKey();

  @WithDefault("https://api.openai.com/v1")
  String openaiBaseUrl();

  @WithDefault("gpt-5-mini")
  String chatModel();

  @WithDefault("text-embedding-3-small")
  String embeddingModel();

  @WithDefault("1536")
  int embeddingDimensions();

  @WithDefault("90")
  int modelTimeoutSeconds();

  @WithDefault("tenant-pe-01:local-demo-key,tenant-other:other-demo-key")
  String apiKeys();

  @WithDefault("local-reviewer-key")
  String reviewerKey();

  @WithDefault("http://localhost:6333")
  String qdrantUrl();

  @WithDefault("")
  java.util.Optional<String> qdrantKey();

  @WithDefault("axiz_payment_knowledge_v2")
  String collection();

  @WithDefault("6")
  int topK();

  @WithDefault("0.45")
  double threshold();

  @WithDefault("500")
  int chunkTokens();

  @WithDefault("60")
  int overlapTokens();

  @WithDefault("14")
  int memoryWindow();

  @WithDefault("true")
  boolean bootstrapEnabled();
}
