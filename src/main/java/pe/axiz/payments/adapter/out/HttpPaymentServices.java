package pe.axiz.payments.adapter.out;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import java.net.URI;
import java.net.http.*;
import java.time.Duration;
import java.util.Map;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import pe.axiz.payments.application.PaymentServices;

@ApplicationScoped
public class HttpPaymentServices implements PaymentServices {
  @Inject ObjectMapper mapper;

  @ConfigProperty(name = "payments.integration-url")
  String baseUrl;

  @ConfigProperty(name = "payments.api-key")
  String key;

  private final HttpClient client =
      HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(2)).build();

  public Map<String, Object> call(String operation, Map<String, Object> request) {
    try {
      var http =
          HttpRequest.newBuilder(URI.create(baseUrl + "/simulator/" + operation))
              .timeout(Duration.ofSeconds(3))
              .header("Content-Type", "application/json")
              .header("X-Api-Key", key)
              .POST(HttpRequest.BodyPublishers.ofString(mapper.writeValueAsString(request)))
              .build();
      var response = client.send(http, HttpResponse.BodyHandlers.ofString());
      if (response.statusCode() != 200)
        throw new IllegalStateException(
            "Fallo de integración: " + operation + " HTTP " + response.statusCode());
      return mapper.readValue(response.body(), new TypeReference<Map<String, Object>>() {});
    } catch (InterruptedException ex) {
      Thread.currentThread().interrupt();
      throw new IllegalStateException("Integración interrumpida", ex);
    } catch (java.io.IOException ex) {
      throw new IllegalStateException("Integración no disponible", ex);
    }
  }
}
