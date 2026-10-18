package pe.axiz.copilot.application;

import static org.junit.jupiter.api.Assertions.*;

import java.util.*;
import org.bsc.langgraph4j.*;
import org.bsc.langgraph4j.checkpoint.PostgresSaver;
import org.bsc.langgraph4j.serializer.plain_text.jackson.JacksonStateSerializer;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.postgresql.ds.PGSimpleDataSource;
import pe.axiz.copilot.adapter.out.*;
import pe.axiz.copilot.domain.*;

@EnabledIfEnvironmentVariable(named = "TEST_JDBC_URL", matches = ".+")
class PostgresPersistenceTest {
  @Test
  void migrationsTenantIsolationMemoryAndCheckpointRestart() throws Exception {
    var ds = new PGSimpleDataSource();
    ds.setURL(System.getenv("TEST_JDBC_URL"));
    ds.setUser(System.getenv().getOrDefault("DB_USER", "axiz"));
    ds.setPassword(System.getenv().getOrDefault("DB_PASSWORD", "axiz-local-password"));
    String schema = "test_" + UUID.randomUUID().toString().replace("-", "");
    ds.setCurrentSchema(schema);
    var flyway =
        Flyway.configure()
            .dataSource(ds)
            .locations("classpath:db/axiz")
            .defaultSchema(schema)
            .schemas(schema)
            .createSchemas(true)
            .cleanDisabled(false)
            .load();
    try {
      flyway.migrate();
      var store = new JdbcStore(ds);
      assertEquals(4, store.payments("tenant-pe-01").size());
      assertEquals(1, store.payments("tenant-other").size());
      assertThrows(CopilotException.class, () -> store.payment("tenant-pe-01", "pay_2001"));
      var cmd =
          new Investigation(
              "tenant-pe-01",
              "pay_1003",
              "conversation-pg",
              "request-pg",
              "¿Es seguro reintentar?",
              false);
      String id = CopilotService.runId(cmd);
      try (var lock = store.lock(cmd.tenantId(), cmd.conversationId())) {
        assertThrows(
            CopilotException.class, () -> store.lock(cmd.tenantId(), cmd.conversationId()));
        store.begin(cmd, id);
        store.begin(cmd, id);
        assertEquals(1, store.memory(cmd.tenantId(), cmd.conversationId(), 14).size());
      }
      var saver =
          PostgresSaver.builder()
              .datasource(ds)
              .createTables(false)
              .stateSerializer(new JacksonStateSerializer<WorkflowState>(WorkflowState::new) {})
              .build();
      var workflow =
          new CopilotWorkflow(new FixtureModel(), new CopilotWorkflowTest.Knowledge(), store, 14);
      var graph = workflow.compile(saver, (t, d) -> {}, true);
      var config = RunnableConfig.builder().threadId(id).build();
      graph.invoke(
          GraphInput.args(Map.of("command", Json.map(cmd), "runId", id, "status", "RUNNING")),
          config);
      assertEquals("human_review", graph.getState(config).nextNodeId());
      var newSaver =
          PostgresSaver.builder()
              .datasource(ds)
              .createTables(false)
              .stateSerializer(new JacksonStateSerializer<WorkflowState>(WorkflowState::new) {})
              .build();
      var restarted = workflow.compile(newSaver, (t, d) -> {}, true);
      var result =
          restarted
              .invoke(GraphInput.resume(Map.of("reviewDecision", "APPROVED")), config)
              .orElseThrow();
      assertEquals("COMPLETED", result.text("status"));
      assertEquals(2, store.memory(cmd.tenantId(), cmd.conversationId(), 14).size());
      assertFalse(restarted.getStateHistory(config).isEmpty());
      assertEquals("COMPLETED", store.run(cmd.tenantId(), id).get("status"));
      store.clear(cmd.tenantId(), cmd.conversationId());
      assertTrue(store.memory(cmd.tenantId(), cmd.conversationId(), 14).isEmpty());
    } finally {
      flyway.clean();
    }
  }
}
