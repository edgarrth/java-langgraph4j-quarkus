package pe.axiz.copilot.application;

public class CopilotException extends RuntimeException {
  public enum Reason {
    NOT_FOUND,
    FORBIDDEN,
    CONFLICT,
    INVALID,
    UNAVAILABLE,
    LIMIT
  }

  private final Reason reason;

  public CopilotException(Reason reason, String message) {
    super(message);
    this.reason = reason;
  }

  public Reason reason() {
    return reason;
  }
}
