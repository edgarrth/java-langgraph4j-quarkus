package pe.axiz.payments.adapter.in;

import jakarta.ws.rs.container.*;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.Provider;
import org.eclipse.microprofile.config.inject.ConfigProperty;

@Provider
public class ApiKeyFilter implements ContainerRequestFilter {
  @ConfigProperty(name = "payments.api-key")
  String key;

  public void filter(ContainerRequestContext context) {
    String path = context.getUriInfo().getPath().replaceFirst("^/+", "");
    if ((path.startsWith("payments") || path.startsWith("simulator"))
        && !key.equals(context.getHeaderString("X-Api-Key")))
      context.abortWith(
          Response.status(401)
              .entity(java.util.Map.of("error", "Clave de acceso inválida"))
              .build());
  }
}
