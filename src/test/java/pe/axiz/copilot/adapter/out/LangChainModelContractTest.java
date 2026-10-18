package pe.axiz.copilot.adapter.out;

import static org.junit.jupiter.api.Assertions.*;

import com.sun.net.httpserver.HttpServer;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.lang.reflect.Proxy;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.Test;
import pe.axiz.copilot.application.*;
import pe.axiz.copilot.domain.*;

class LangChainModelContractTest {
  @Test
  void realSdkSerializesToolsSchemaEmbeddingsAndSse() throws Exception {
    var sawTools = new AtomicBoolean();
    var sawSchema = new AtomicBoolean();
    var sawEmbeddings = new AtomicBoolean();
    var server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
    var report =
        new Report(
            "pay_1002",
            "El emisor rechazó sin motivo específico.",
            Report.Disposition.DECLINE_EXPLAINED,
            Report.Confidence.HIGH,
            false,
            List.of(new Report.Evidence("PAYMENT_DATABASE", "PAYMENT:pay_1002", "DO_NOT_HONOR")),
            List.of("Consultar al emisor"),
            List.of());
    server.createContext(
        "/v1/",
        exchange -> {
          try {
            var body =
                Json.object(
                    new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            String response;
            if (exchange.getRequestURI().getPath().endsWith("embeddings")) {
              sawEmbeddings.set(true);
              response =
                  Json.write(
                      Map.of(
                          "object",
                          "list",
                          "model",
                          "text-embedding-3-small",
                          "data",
                          List.of(
                              Map.of(
                                  "object",
                                  "embedding",
                                  "index",
                                  0,
                                  "embedding",
                                  List.of(0.1, 0.2, 0.3))),
                          "usage",
                          Map.of("prompt_tokens", 4, "total_tokens", 4)));
            } else if (Boolean.TRUE.equals(body.get("stream"))) {
              exchange.getResponseHeaders().set("Content-Type", "text/event-stream");
              exchange.sendResponseHeaders(200, 0);
              for (String text : List.of("El emisor ", "rechazó el pago.")) {
                String event =
                    Json.write(
                        Map.of(
                            "id",
                            "test-stream",
                            "object",
                            "chat.completion.chunk",
                            "created",
                            1,
                            "model",
                            "gpt-5-mini",
                            "choices",
                            List.of(Map.of("index", 0, "delta", Map.of("content", text)))));
                exchange
                    .getResponseBody()
                    .write(("data: " + event + "\n\n").getBytes(StandardCharsets.UTF_8));
                exchange.getResponseBody().flush();
              }
              exchange.getResponseBody().write("data: [DONE]\n\n".getBytes(StandardCharsets.UTF_8));
              exchange.close();
              return;
            } else {
              Object message;
              String finish;
              if (body.containsKey("tools")) {
                sawTools.set(true);
                message =
                    Map.of(
                        "role",
                        "assistant",
                        "tool_calls",
                        List.of(
                            Map.of(
                                "id",
                                "call-1",
                                "type",
                                "function",
                                "function",
                                Map.of(
                                    "name",
                                    "getPayment",
                                    "arguments",
                                    "{\"paymentId\":\"pay_1002\"}"))));
                finish = "tool_calls";
              } else {
                var format = (Map<?, ?>) body.get("response_format");
                sawSchema.set(format != null && format.get("type").equals("json_schema"));
                message = Map.of("role", "assistant", "content", Json.write(report));
                finish = "stop";
              }
              response =
                  Json.write(
                      Map.of(
                          "id",
                          "test-chat",
                          "object",
                          "chat.completion",
                          "created",
                          1,
                          "model",
                          "gpt-5-mini",
                          "choices",
                          List.of(Map.of("index", 0, "message", message, "finish_reason", finish)),
                          "usage",
                          Map.of("prompt_tokens", 10, "completion_tokens", 5, "total_tokens", 15)));
            }
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            byte[] bytes = response.getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, bytes.length);
            exchange.getResponseBody().write(bytes);
            exchange.close();
          } catch (Exception error) {
            exchange.sendResponseHeaders(500, -1);
            exchange.close();
          }
        });
    server.start();
    try {
      String base = "http://127.0.0.1:" + server.getAddress().getPort() + "/v1";
      var config =
          (CopilotConfig)
              Proxy.newProxyInstance(
                  getClass().getClassLoader(),
                  new Class<?>[] {CopilotConfig.class},
                  (proxy, method, args) ->
                      switch (method.getName()) {
                        case "provider" -> "OPENAI";
                        case "openaiKey" -> Optional.of("test-key");
                        case "openaiBaseUrl" -> base;
                        case "chatModel" -> "gpt-5-mini";
                        case "embeddingModel" -> "text-embedding-3-small";
                        case "embeddingDimensions" -> 1536;
                        case "modelTimeoutSeconds" -> 5;
                        case "topK" -> 6;
                        case "threshold" -> 0.45;
                        case "memoryWindow" -> 14;
                        case "collection" -> "test_collection";
                        default -> throw new UnsupportedOperationException(method.getName());
                      });
      var model = new LangChainModel();
      model.config = config;
      var listener = new ModelMetrics();
      listener.metrics = new SimpleMeterRegistry();
      model.modelMetrics = listener;
      model.init();
      var messages =
          List.of(Map.<String, Object>of("role", "user", "content", "Pago autorizado: pay_1002"));
      var result = model.reason(messages);
      assertTrue(sawTools.get());
      assertEquals("getPayment", result.calls().getFirst().name());
      assertEquals(10, result.inputTokens());
      assertEquals(report, Json.read(model.structured(messages), Report.class));
      assertTrue(sawSchema.get());
      var tokens = new ArrayList<String>();
      String text = model.narrate("¿Qué pasó?", report, tokens::add);
      assertFalse(tokens.isEmpty());
      assertTrue(text.contains("rechazó"));
      assertEquals(3, model.embed(List.of("conocimiento")).getFirst().length);
      assertTrue(sawEmbeddings.get());
      assertTrue(listener.metrics.get("copilot.model.requests").counter().count() >= 3);
    } finally {
      server.stop(0);
    }
  }
}
