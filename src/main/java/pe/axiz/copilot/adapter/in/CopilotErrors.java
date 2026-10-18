package pe.axiz.copilot.adapter.in;

import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.*;
import java.util.Map;
import pe.axiz.copilot.application.CopilotException;

@Provider
public class CopilotErrors implements ExceptionMapper<CopilotException> {
  public Response toResponse(CopilotException error) {
    int code =
        switch (error.reason()) {
          case NOT_FOUND -> 404;
          case FORBIDDEN -> 403;
          case CONFLICT -> 409;
          case INVALID -> 400;
          case LIMIT -> 422;
          case UNAVAILABLE -> 503;
        };
    return Response.status(code)
        .entity(Map.of("error", error.getMessage(), "code", error.reason().name()))
        .build();
  }
}
