package pe.axiz.copilot.adapter.out;

import dev.langchain4j.data.document.Document;
import dev.langchain4j.data.document.splitter.DocumentSplitters;
import dev.langchain4j.model.openai.OpenAiTokenCountEstimator;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import java.net.URI;
import java.net.http.*;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.*;
import pe.axiz.copilot.application.*;

@ApplicationScoped
public class QdrantKnowledge implements Ports.Knowledge {
  @Inject Ports.Model model;
  @Inject CopilotConfig config;
  private final HttpClient http =
      HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(3)).build();

  public boolean ready() {
    return !request("GET", path(), null, true).isEmpty();
  }

  public String collection() {
    return config.collection()
        + "_"
        + UUID.nameUUIDFromBytes(model.embeddingFingerprint().getBytes(StandardCharsets.UTF_8))
            .toString()
            .substring(0, 8);
  }

  private Map<String, Object> request(
      String method, String path, Object data, boolean missingAllowed) {
    try {
      var builder =
          HttpRequest.newBuilder(URI.create(config.qdrantUrl() + path))
              .timeout(Duration.ofSeconds(30))
              .header("Content-Type", "application/json");
      config.qdrantKey().filter(k -> !k.isBlank()).ifPresent(k -> builder.header("api-key", k));
      builder.method(
          method,
          data == null
              ? HttpRequest.BodyPublishers.noBody()
              : HttpRequest.BodyPublishers.ofString(Json.write(data)));
      var r = http.send(builder.build(), HttpResponse.BodyHandlers.ofString());
      if (missingAllowed && r.statusCode() == 404) return Map.of();
      if (r.statusCode() / 100 != 2)
        throw new IllegalStateException("Qdrant rechazó la operación HTTP " + r.statusCode());
      return Json.object(r.body());
    } catch (InterruptedException ex) {
      Thread.currentThread().interrupt();
      throw new IllegalStateException("Consulta vectorial interrumpida", ex);
    } catch (java.io.IOException ex) {
      throw new IllegalStateException("Qdrant no disponible", ex);
    }
  }

  private String path() {
    return "/collections/" + collection();
  }

  private static Map<String, Object> match(String key, Object value) {
    return Map.of("key", key, "match", Map.of("value", value));
  }

  public List<Ports.Source> search(String question, String tenant) {
    var vector = model.embed(List.of(question)).getFirst();
    var filter =
        Map.of(
            "must",
            List.of(match("domain", "payments")),
            "should",
            List.of(match("tenant", tenant), match("tenant", "PUBLIC")));
    var r =
        request(
            "POST",
            path() + "/points/query",
            Map.of(
                "query",
                vector,
                "limit",
                config.topK(),
                "score_threshold",
                config.threshold(),
                "with_payload",
                true,
                "filter",
                filter),
            false);
    var result = (Map<?, ?>) r.get("result");
    var points = (List<?>) result.get("points");
    var out = new ArrayList<Ports.Source>();
    for (var value : points) {
      var point = (Map<?, ?>) value;
      var payload = (Map<?, ?>) point.get("payload");
      out.add(
          new Ports.Source(
              point.get("id").toString(),
              payload.get("text").toString(),
              payload.get("source").toString(),
              ((Number) point.get("score")).doubleValue()));
    }
    return out;
  }

  public synchronized int ingest(
      List<Map<String, Object>> documents, String tenant, boolean replace) {
    if (documents.isEmpty() || documents.size() > 40)
      throw new IllegalArgumentException("Se requieren entre 1 y 40 documentos");
    if (config.chunkTokens() < 100
        || config.chunkTokens() > 2000
        || config.overlapTokens() < 0
        || config.overlapTokens() >= config.chunkTokens())
      throw new IllegalArgumentException("Chunking inválido");
    var chunks = new ArrayList<Map<String, Object>>();
    var texts = new ArrayList<String>();
    var ids = new HashSet<String>();
    var splitter =
        DocumentSplitters.recursive(
            config.chunkTokens(),
            config.overlapTokens(),
            new OpenAiTokenCountEstimator("gpt-4o-mini"));
    for (var doc : documents) {
      String id = Objects.toString(doc.get("id"), "");
      String content = Objects.toString(doc.get("content"), "");
      var metadata = doc.get("metadata") instanceof Map<?, ?> m ? m : Map.of();
      String source = Objects.toString(metadata.get("source"), id);
      if (!id.matches("[a-zA-Z0-9_-]{1,100}")
          || content.isBlank()
          || content.length() > 50000
          || source.length() > 200
          || !ids.add(id)) throw new IllegalArgumentException("Documento inválido o duplicado");
      int index = 0;
      for (var segment : splitter.split(Document.from(content))) {
        var payload =
            Map.<String, Object>of(
                "tenant",
                tenant,
                "domain",
                "payments",
                "document",
                id,
                "source",
                source,
                "text",
                segment.text(),
                "ordinal",
                index++);
        chunks.add(payload);
        texts.add(segment.text());
      }
    }
    if (chunks.size() > 300)
      throw new IllegalArgumentException("Se excedió el máximo de chunks por carga");
    var vectors = new ArrayList<float[]>();
    for (int i = 0; i < texts.size(); i += 32)
      vectors.addAll(model.embed(texts.subList(i, Math.min(i + 32, texts.size()))));
    if (vectors.size() != chunks.size() || vectors.isEmpty())
      throw new IllegalStateException("El proveedor no devolvió los embeddings esperados");
    int dimension = vectors.getFirst().length;
    if (request("GET", path(), null, true).isEmpty())
      request(
          "PUT", path(), Map.of("vectors", Map.of("size", dimension, "distance", "Cosine")), false);
    for (String field : List.of("tenant", "domain", "document"))
      request(
          "PUT",
          path() + "/index?wait=true",
          Map.of("field_name", field, "field_schema", "keyword"),
          false);
    var must = new ArrayList<Object>();
    must.add(match("tenant", tenant));
    if (!replace) must.add(Map.of("key", "document", "match", Map.of("any", new ArrayList<>(ids))));
    request(
        "POST", path() + "/points/delete?wait=true", Map.of("filter", Map.of("must", must)), false);
    var points = new ArrayList<Map<String, Object>>();
    for (int i = 0; i < chunks.size(); i++) {
      var payload = chunks.get(i);
      String id =
          UUID.nameUUIDFromBytes(
                  (tenant + ":" + payload.get("document") + ":" + payload.get("ordinal"))
                      .getBytes(StandardCharsets.UTF_8))
              .toString();
      points.add(Map.of("id", id, "vector", vectors.get(i), "payload", payload));
    }
    request("PUT", path() + "/points?wait=true", Map.of("points", points), false);
    return points.size();
  }
}
