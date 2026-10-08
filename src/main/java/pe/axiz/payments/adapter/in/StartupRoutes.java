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
            "Rutas: POST /payments; POST /payments/stream; GET /payments/graph; GET /payments/{id};"
                + " GET /payments/{id}/history; GET /payments/{id}/checkpoints/{checkpointId}; POST"
                + " /payments/{id}/resume; POST /payments/{id}/retry; POST"
                + " /simulator/{risk|merchant|authorize|capture|void}; GET /q/health; GET"
                + " /q/health/live; GET /q/health/ready; GET /q/openapi; GET /q/swagger-ui");
  }
}
