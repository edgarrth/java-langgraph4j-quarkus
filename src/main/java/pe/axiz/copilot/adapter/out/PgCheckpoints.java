package pe.axiz.copilot.adapter.out;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import javax.sql.DataSource;
import org.bsc.langgraph4j.checkpoint.*;
import org.bsc.langgraph4j.serializer.plain_text.jackson.JacksonStateSerializer;
import pe.axiz.copilot.application.*;

@ApplicationScoped
public class PgCheckpoints implements Ports.Checkpoints {
  @Inject DataSource datasource;

  public BaseCheckpointSaver create() {
    try {
      return PostgresSaver.builder()
          .datasource(datasource)
          .stateSerializer(new JacksonStateSerializer<WorkflowState>(WorkflowState::new) {})
          .createTables(false)
          .build();
    } catch (Exception ex) {
      throw new IllegalStateException("No se pudo preparar la persistencia del grafo", ex);
    }
  }
}
