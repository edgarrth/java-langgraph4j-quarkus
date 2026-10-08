package pe.axiz.payments.domain;

import jakarta.validation.constraints.*;
import java.util.Map;

public record Payment(
    @NotBlank @Pattern(regexp = "[a-zA-Z0-9-]{1,64}") String id,
    @Min(1) @Max(100000000) long amountMinor,
    @Pattern(regexp = "PEN|USD") @NotNull String currency,
    @Pattern(regexp = "NORMAL|REVIEW|DECLINE|FAILOVER|COMPENSATE") @NotNull String scenario) {
  public Map<String, Object> state() {
    return Map.of(
        "id",
        id,
        "amountMinor",
        amountMinor,
        "currency",
        currency,
        "scenario",
        scenario,
        "attempt",
        0,
        "status",
        "RUNNING");
  }
}
