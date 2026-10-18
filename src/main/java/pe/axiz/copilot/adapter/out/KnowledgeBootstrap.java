package pe.axiz.copilot.adapter.out;

import io.quarkus.runtime.StartupEvent;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import jakarta.inject.Inject;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.*;
import org.jboss.logging.Logger;
import pe.axiz.copilot.application.*;

@ApplicationScoped
public class KnowledgeBootstrap {
  @Inject Ports.Model model;
  @Inject Ports.Store store;
  @Inject Ports.Knowledge knowledge;
  @Inject CopilotConfig config;

  @SuppressWarnings("unchecked")
  void start(@Observes StartupEvent event) throws Exception {
    if (!config.bootstrapEnabled()) return;
    try (var lease = store.lock("PUBLIC", "knowledge-bootstrap")) {
      byte[] data;
      try (var in = getClass().getResourceAsStream("/datasets/payment-knowledge.json")) {
        if (in == null) throw new IllegalStateException("No se encontró el dataset canónico");
        data = in.readAllBytes();
      }
      String fingerprint =
          HexFormat.of()
              .formatHex(
                  MessageDigest.getInstance("SHA-256")
                      .digest(
                          (new String(data, StandardCharsets.UTF_8)
                                  + model.embeddingFingerprint()
                                  + config.collection()
                                  + config.chunkTokens()
                                  + ":"
                                  + config.overlapTokens())
                              .getBytes(StandardCharsets.UTF_8)));
      if (!store.bootstrapped(fingerprint) || !knowledge.ready()) {
        var document = Json.object(new String(data, StandardCharsets.UTF_8));
        int chunks =
            knowledge.ingest((List<Map<String, Object>>) document.get("documents"), "PUBLIC", true);
        store.bootstrap(fingerprint, chunks);
        Logger.getLogger(getClass())
            .infof(
                "Conocimiento inicial preparado: %d chunks; proveedor %s",
                chunks, model.provider());
      }
    }
  }
}
