package pe.axiz.payments.application;

import java.util.Map;
import org.bsc.langgraph4j.state.AgentState;

public class PaymentState extends AgentState {
  public PaymentState(Map<String, Object> data) {
    super(data);
  }

  public String text(String key) {
    return this.<String>value(key).orElse("");
  }

  public int attempt() {
    return ((Number) data().getOrDefault("attempt", 0)).intValue();
  }
}
