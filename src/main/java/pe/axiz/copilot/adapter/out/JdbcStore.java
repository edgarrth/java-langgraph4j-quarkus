package pe.axiz.copilot.adapter.out;

import static pe.axiz.copilot.application.CopilotException.Reason.*;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.sql.*;
import java.util.*;
import javax.sql.DataSource;
import pe.axiz.copilot.application.*;
import pe.axiz.copilot.domain.*;

@ApplicationScoped
public class JdbcStore implements Ports.Store {
  @Inject DataSource datasource;

  public JdbcStore() {}

  public JdbcStore(DataSource datasource) {
    this.datasource = datasource;
  }

  private List<Map<String, Object>> query(String sql, Object... args) {
    try (var c = datasource.getConnection();
        var s = c.prepareStatement(sql)) {
      bind(s, args);
      try (var r = s.executeQuery()) {
        var rows = new ArrayList<Map<String, Object>>();
        var meta = r.getMetaData();
        while (r.next()) {
          var row = new LinkedHashMap<String, Object>();
          for (int i = 1; i <= meta.getColumnCount(); i++) {
            Object v = r.getObject(i);
            row.put(
                meta.getColumnLabel(i),
                v instanceof Number || v instanceof Boolean || v == null ? v : v.toString());
          }
          rows.add(row);
        }
        return rows;
      }
    } catch (SQLException ex) {
      throw new IllegalStateException("No se pudo consultar la persistencia", ex);
    }
  }

  private static void bind(PreparedStatement s, Object... args) throws SQLException {
    for (int i = 0; i < args.length; i++) s.setObject(i + 1, args[i]);
  }

  private static int update(Connection c, String sql, Object... args) throws SQLException {
    try (var s = c.prepareStatement(sql)) {
      bind(s, args);
      return s.executeUpdate();
    }
  }

  private void update(String sql, Object... args) {
    try (var c = datasource.getConnection()) {
      update(c, sql, args);
    } catch (SQLException ex) {
      throw new IllegalStateException("No se pudo actualizar la persistencia", ex);
    }
  }

  private Map<String, Object> one(List<Map<String, Object>> rows) {
    if (rows.isEmpty())
      throw new CopilotException(NOT_FOUND, "Recurso no encontrado en el ámbito autorizado");
    return rows.getFirst();
  }

  public Map<String, Object> payment(String tenant, String payment) {
    return one(
        query(
            "SELECT id,merchant_id,amount,currency,status,processor_reference,decline_code,created_at,updated_at FROM payments WHERE tenant_id=? AND id=?",
            tenant,
            payment));
  }

  public List<Map<String, Object>> payments(String tenant) {
    return query(
        "SELECT id,amount,currency,status,decline_code FROM payments WHERE tenant_id=? ORDER BY id",
        tenant);
  }

  public List<Map<String, Object>> timeline(String tenant, String payment) {
    payment(tenant, payment);
    return query(
        "SELECT e.sequence_no,e.event_type,e.occurred_at,e.details FROM payment_events e JOIN payments p ON p.id=e.payment_id WHERE p.tenant_id=? AND p.id=? ORDER BY e.sequence_no",
        tenant,
        payment);
  }

  public Map<String, Object> merchant(String tenant, String payment) {
    return one(
        query(
            "SELECT m.id,m.name,m.country,m.risk_tier FROM merchants m JOIN payments p ON p.merchant_id=m.id AND p.tenant_id=m.tenant_id WHERE p.tenant_id=? AND p.id=?",
            tenant,
            payment));
  }

  public List<Map<String, Object>> memory(String tenant, String conversation, int max) {
    var rows =
        query(
            "SELECT role,content,run_id,created_at FROM copilot_messages WHERE tenant_id=? AND conversation_id=? ORDER BY id DESC LIMIT ?",
            tenant,
            conversation,
            max);
    Collections.reverse(rows);
    return rows;
  }

  public List<Map<String, Object>> conversations(String tenant) {
    return query(
        "SELECT c.id,c.payment_id,c.created_at,(SELECT r.status FROM copilot_runs r WHERE r.tenant_id=c.tenant_id AND r.conversation_id=c.id ORDER BY r.created_at DESC LIMIT 1) AS status FROM copilot_conversations c WHERE c.tenant_id=? ORDER BY c.created_at DESC LIMIT 100",
        tenant);
  }

  public void clear(String tenant, String conversation) {
    update(
        "DELETE FROM copilot_messages WHERE tenant_id=? AND conversation_id=?",
        tenant,
        conversation);
  }

  public void begin(Investigation cmd, String id) {
    payment(cmd.tenantId(), cmd.paymentId());
    try (var c = datasource.getConnection()) {
      c.setAutoCommit(false);
      try {
        update(
            c,
            "INSERT INTO copilot_conversations(tenant_id,id,payment_id) VALUES(?,?,?) ON CONFLICT DO NOTHING",
            cmd.tenantId(),
            cmd.conversationId(),
            cmd.paymentId());
        try (var s =
            c.prepareStatement(
                "SELECT payment_id FROM copilot_conversations WHERE tenant_id=? AND id=?")) {
          bind(s, cmd.tenantId(), cmd.conversationId());
          try (var r = s.executeQuery()) {
            if (!r.next() || !r.getString(1).equals(cmd.paymentId()))
              throw new CopilotException(
                  CONFLICT,
                  "La conversación ya corresponde a otro pago; se requiere una nueva conversación");
          }
        }
        update(
            c,
            "INSERT INTO copilot_runs(id,tenant_id,conversation_id,request_id,payment_id,question,review_requested,status) VALUES(?,?,?,?,?,?,?,'RUNNING') ON CONFLICT DO NOTHING",
            id,
            cmd.tenantId(),
            cmd.conversationId(),
            cmd.requestId(),
            cmd.paymentId(),
            cmd.question(),
            cmd.reviewRequested());
        try (var s =
            c.prepareStatement(
                "SELECT question,review_requested FROM copilot_runs WHERE tenant_id=? AND id=?")) {
          bind(s, cmd.tenantId(), id);
          try (var r = s.executeQuery()) {
            if (!r.next()
                || !r.getString(1).equals(cmd.question())
                || r.getBoolean(2) != cmd.reviewRequested())
              throw new CopilotException(CONFLICT, "El requestId ya corresponde a otra solicitud");
          }
        }
        update(
            c,
            "INSERT INTO copilot_messages(tenant_id,conversation_id,run_id,role,content) VALUES(?,?,?,'user',?) ON CONFLICT DO NOTHING",
            cmd.tenantId(),
            cmd.conversationId(),
            id,
            cmd.question());
        c.commit();
      } catch (Exception ex) {
        c.rollback();
        throw ex;
      }
    } catch (SQLException ex) {
      throw new IllegalStateException("No se pudo iniciar la investigación", ex);
    }
  }

  public void finish(Investigation cmd, String id, Report report, String narrative) {
    try (var c = datasource.getConnection()) {
      c.setAutoCommit(false);
      try {
        update(
            c,
            "UPDATE copilot_runs SET status='COMPLETED',report=?,narrative=? WHERE tenant_id=? AND id=?",
            Json.write(report),
            narrative,
            cmd.tenantId(),
            id);
        update(
            c,
            "INSERT INTO copilot_messages(tenant_id,conversation_id,run_id,role,content) VALUES(?,?,?,'assistant',?) ON CONFLICT DO NOTHING",
            cmd.tenantId(),
            cmd.conversationId(),
            id,
            narrative);
        c.commit();
      } catch (Exception ex) {
        c.rollback();
        throw ex;
      }
    } catch (SQLException ex) {
      throw new IllegalStateException("No se pudo completar la investigación", ex);
    }
  }

  public Map<String, Object> run(String tenant, String id) {
    return one(
        query(
            "SELECT id,conversation_id,payment_id,request_id,question,review_requested,status,report,narrative FROM copilot_runs WHERE tenant_id=? AND id=?",
            tenant,
            id));
  }

  public void status(String tenant, String id, String status) {
    update("UPDATE copilot_runs SET status=? WHERE tenant_id=? AND id=?", status, tenant, id);
  }

  public AutoCloseable lock(String tenant, String conversation) {
    Connection connection = null;
    try {
      long key =
          ByteBuffer.wrap(
                  MessageDigest.getInstance("SHA-256")
                      .digest((tenant + ":" + conversation).getBytes(StandardCharsets.UTF_8)))
              .getLong();
      connection = datasource.getConnection();
      try (var statement = connection.prepareStatement("SELECT pg_try_advisory_lock(?)")) {
        statement.setLong(1, key);
        try (var rows = statement.executeQuery()) {
          rows.next();
          if (!rows.getBoolean(1))
            throw new CopilotException(CONFLICT, "La conversación tiene otra operación activa");
        }
      }
      final Connection held = connection;
      return () -> {
        try (held;
            var statement = held.prepareStatement("SELECT pg_advisory_unlock(?)")) {
          statement.setLong(1, key);
          statement.execute();
        }
      };
    } catch (Exception error) {
      if (connection != null)
        try {
          connection.close();
        } catch (SQLException close) {
          error.addSuppressed(close);
        }
      if (error instanceof CopilotException exception) throw exception;
      throw new IllegalStateException("No se pudo bloquear la conversación", error);
    }
  }

  public boolean bootstrapped(String fingerprint) {
    return !query("SELECT fingerprint FROM copilot_bootstrap WHERE fingerprint=?", fingerprint)
        .isEmpty();
  }

  public void bootstrap(String fingerprint, int chunks) {
    update(
        "INSERT INTO copilot_bootstrap(fingerprint,chunks) VALUES(?,?) ON CONFLICT DO NOTHING",
        fingerprint,
        chunks);
  }
}
