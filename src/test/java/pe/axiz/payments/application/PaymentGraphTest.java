package pe.axiz.payments.application;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.file.Files;
import java.util.*;
import org.junit.jupiter.api.Test;
import pe.axiz.payments.adapter.in.SimulatorResource;
import pe.axiz.payments.domain.Payment;

class PaymentGraphTest {
  private PaymentGraph graph(String folder) throws Exception {
    var graph = new PaymentGraph();
    graph.services = (op, data) -> new SimulatorResource().invoke(op, data);
    graph.folder = folder;
    graph.init();
    return graph;
  }

  private Map<?, ?> state(Map<String, Object> response) {
    return (Map<?, ?>) response.get("state");
  }

  @Test
  void normalAndIdempotency() throws Exception {
    var graph = graph(Files.createTempDirectory("graph").toString());
    var payment = new Payment("normal", 100, "PEN", "NORMAL");
    assertEquals("CAPTURED", state(graph.start(payment)).get("status"));
    assertEquals(1, state(graph.start(payment)).get("attempt"));
    assertThrows(
        jakarta.ws.rs.ClientErrorException.class,
        () -> graph.start(new Payment("normal", 101, "PEN", "NORMAL")));
    assertTrue(graph.history("normal").size() > 5);
  }

  @Test
  void reviewSurvivesRestart() throws Exception {
    var folder = Files.createTempDirectory("graph").toString();
    var first = graph(folder);
    assertEquals(
        "approval", first.start(new Payment("review", 100, "USD", "REVIEW")).get("nextNode"));
    var restarted = graph(folder);
    assertEquals("CAPTURED", state(restarted.resume("review", "APPROVED")).get("status"));
    assertThrows(
        jakarta.ws.rs.ClientErrorException.class, () -> restarted.resume("review", "APPROVED"));
  }

  @Test
  void reviewRejection() throws Exception {
    var graph = graph(Files.createTempDirectory("graph").toString());
    graph.start(new Payment("review", 100, "PEN", "REVIEW"));
    assertEquals("REJECTED", state(graph.resume("review", "REJECTED")).get("status"));
  }

  @Test
  void highRiskRejection() throws Exception {
    var graph = graph(Files.createTempDirectory("graph").toString());
    assertEquals(
        "REJECTED",
        state(graph.start(new Payment("decline", 100, "PEN", "DECLINE"))).get("status"));
  }

  @Test
  void failoverAndCompensation() throws Exception {
    var graph = graph(Files.createTempDirectory("graph").toString());
    var state = state(graph.start(new Payment("failover", 100, "PEN", "FAILOVER")));
    assertEquals("CAPTURED", state.get("status"));
    assertEquals(2, state.get("attempt"));
    assertEquals("secondary", state.get("acquirer"));
    assertEquals(
        "COMPENSATED",
        state(graph.start(new Payment("void", 100, "PEN", "COMPENSATE"))).get("status"));
  }

  @Test
  void checkpointAndStream() throws Exception {
    var graph = graph(Files.createTempDirectory("graph").toString());
    var events = new ArrayList<Map<String, Object>>();
    graph.stream(new Payment("stream", 100, "PEN", "NORMAL"), events::add);
    assertTrue(events.size() > 5);
    var history = graph.history("stream");
    assertNotNull(graph.checkpoint("stream", (String) history.get(0).get("checkpointId")));
  }

  @Test
  void failedIntegrationCanResume() throws Exception {
    var graph = graph(Files.createTempDirectory("graph").toString());
    var available = new java.util.concurrent.atomic.AtomicBoolean(false);
    graph.services =
        (op, data) -> {
          if (!available.get()) throw new IllegalStateException("Servicio no disponible");
          return new SimulatorResource().invoke(op, data);
        };
    assertThrows(
        RuntimeException.class, () -> graph.start(new Payment("recovery", 100, "PEN", "NORMAL")));
    available.set(true);
    assertEquals("CAPTURED", state(graph.retry("recovery")).get("status"));
  }
}
