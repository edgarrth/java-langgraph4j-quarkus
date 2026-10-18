package pe.axiz.copilot.adapter.in;

import jakarta.ws.rs.container.*;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.Provider;
import java.util.*;

@Provider
public class CopilotSecurity implements ContainerRequestFilter {
  @jakarta.inject.Inject jakarta.inject.Provider<pe.axiz.copilot.adapter.out.CopilotConfig> config;

  public void filter(ContainerRequestContext ctx) {
    String path = ctx.getUriInfo().getPath().replaceFirst("^/+", "");
    if (!path.startsWith("api/v1")) return;
    String tenant = ctx.getHeaderString("X-Tenant-Id");
    String key = ctx.getHeaderString("X-Api-Key");
    boolean allowed = false;
    for (String pair : config.get().apiKeys().split(",")) {
      var parts = pair.split(":", 2);
      if (parts.length == 2 && parts[0].equals(tenant) && constantEquals(parts[1], key))
        allowed = true;
    }
    if (!allowed)
      ctx.abortWith(
          Response.status(401).entity(Map.of("error", "Credenciales o tenant inválidos")).build());
  }

  public static boolean constantEquals(String a, String b) {
    return b != null
        && java.security.MessageDigest.isEqual(
            a.getBytes(java.nio.charset.StandardCharsets.UTF_8),
            b.getBytes(java.nio.charset.StandardCharsets.UTF_8));
  }
}
