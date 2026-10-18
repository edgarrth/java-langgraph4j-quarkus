package pe.axiz.payments.adapter.in;

import io.quarkus.runtime.StartupEvent;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import org.jboss.logging.Logger;

@ApplicationScoped
public class StartupRoutes {
  void start(@Observes StartupEvent event) {
    Logger.getLogger(StartupRoutes.class)
        .info(
            "Copilot: GET /api/v1/runtime; GET /api/v1/payments; GET /api/v1/payments/{id}; GET /api/v1/payments/{id}/timeline; POST /api/v1/payments/{id}/investigations; POST /api/v1/payments/{id}/investigations/stream; GET /api/v1/conversations; GET y DELETE /api/v1/conversations/{id}/messages; GET /api/v1/runs/{id}; GET /api/v1/runs/{id}/checkpoints; GET /api/v1/runs/{id}/checkpoints/{checkpoint}; POST /api/v1/runs/{id}/review; POST /api/v1/runs/{id}/review/stream; POST /api/v1/runs/{id}/retry/stream; POST /api/v1/knowledge/documents; GET /api/v1/graph; GET /q/metrics; Interfaz: http://localhost:4200");
    Logger.getLogger(StartupRoutes.class)
        .info(
            "Rutas: POST /payments; POST /payments/stream; GET /payments/graph; GET /payments/{id};"
                + " GET /payments/{id}/history; GET /payments/{id}/checkpoints/{checkpointId}; POST"
                + " /payments/{id}/resume; POST /payments/{id}/retry; POST"
                + " /simulator/{risk|merchant|authorize|capture|void}; GET /q/health; GET"
                + " /q/health/live; GET /q/health/ready; GET /q/openapi; GET /q/swagger-ui");
  }
}
