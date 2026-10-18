package pe.axiz.copilot.application;

import java.util.*;
import org.bsc.langgraph4j.state.AgentState;
import pe.axiz.copilot.domain.*;

public class WorkflowState extends AgentState {
  public WorkflowState(Map<String, Object> data) {
    super(data);
  }

  public String text(String key) {
    return this.<String>value(key).orElse("");
  }

  @SuppressWarnings("unchecked")
  public List<Map<String, Object>> rows(String key) {
    return (List<Map<String, Object>>) data().getOrDefault(key, List.of());
  }

  public int number(String key) {
    return ((Number) data().getOrDefault(key, 0)).intValue();
  }

  public Investigation command() {
    return Json.read(Json.write(data().get("command")), Investigation.class);
  }

  public Report report() {
    return Json.read(Json.write(data().get("report")), Report.class);
  }
}
