package com.ispf.server.application.data;

import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.UncategorizedSQLException;
import org.springframework.stereotype.Service;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Savepoint;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * Transactional outbox and idempotent inbox for any {@code app_*} connector.
 * A delivery attempt that throws stays pending. After five failures the row is dead-lettered.
 */
@Service
public class ApplicationMessageRelay {

    static final int DEAD_LETTER_AFTER = 5;

    /** Lease longer than HttpOutboxDelivery connect+read timeouts so a live worker keeps the claim. */
    private static final Duration CLAIM_LEASE = Duration.ofSeconds(30);

    private final JdbcTemplate jdbcTemplate;

    public ApplicationMessageRelay(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    /**
     * Idempotent DDL for unit tests and profiles without Flyway. Production schema is V98 plus V100.
     */
    public void ensureTables() {
        jdbcTemplate.execute("""
                CREATE TABLE IF NOT EXISTS application_outbox (
                    id VARCHAR(64) PRIMARY KEY,
                    app_id VARCHAR(128) NOT NULL,
                    connector_id VARCHAR(128) NOT NULL,
                    message_id VARCHAR(256) NOT NULL,
                    body TEXT NOT NULL,
                    status VARCHAR(32) NOT NULL,
                    attempts INT NOT NULL,
                    created_at TIMESTAMP NOT NULL
                )
                """);
        jdbcTemplate.execute("""
                ALTER TABLE application_outbox ADD COLUMN IF NOT EXISTS claimed_until TIMESTAMP NULL
                """);
        jdbcTemplate.execute("""
                CREATE UNIQUE INDEX IF NOT EXISTS uq_application_outbox_idempotency
                ON application_outbox (app_id, connector_id, message_id)
                """);
        jdbcTemplate.execute("""
                CREATE TABLE IF NOT EXISTS application_inbox (
                    connector_id VARCHAR(128) NOT NULL,
                    external_message_id VARCHAR(256) NOT NULL,
                    received_at TIMESTAMP NOT NULL,
                    PRIMARY KEY (connector_id, external_message_id)
                )
                """);
    }

    /**
     * Inserts a pending row, or returns the existing row when the same app, connector,
     * and message id were already accepted. The message id is the Idempotency-Key.
     */
    public String enqueue(String appId, String connectorId, String messageId, String body) {
        return enqueueReturning(appId, connectorId, messageId, body).id();
    }

    public Enqueued enqueueReturning(String appId, String connectorId, String messageId, String body) {
        Optional<OutboxMessage> existing = findMessage(appId, connectorId, messageId);
        if (existing.isPresent()) {
            return new Enqueued(existing.get(), false);
        }
        String id = UUID.randomUUID().toString();
        String payload = body == null ? "" : body;
        Timestamp createdAt = Timestamp.from(Instant.now());
        try {
            return jdbcTemplate.execute((Connection connection) -> insertOutbox(
                    connection, id, appId, connectorId, messageId, payload, createdAt
            ));
        } catch (DataAccessException ex) {
            if (isUniqueViolation(ex)) {
                return findMessage(appId, connectorId, messageId)
                        .map(row -> new Enqueued(row, false))
                        .orElseThrow(() -> ex);
            }
            throw ex;
        }
    }

    private Enqueued insertOutbox(
            Connection connection,
            String id,
            String appId,
            String connectorId,
            String messageId,
            String body,
            Timestamp createdAt
    ) throws SQLException {
        Savepoint savepoint = connection.getAutoCommit() ? null : connection.setSavepoint();
        try (PreparedStatement statement = connection.prepareStatement("""
                INSERT INTO public.application_outbox (id, app_id, connector_id, message_id, body, status, attempts, created_at)
                VALUES (?, ?, ?, ?, ?, 'PENDING', 0, ?)
                """)) {
            statement.setString(1, id);
            statement.setString(2, appId);
            statement.setString(3, connectorId);
            statement.setString(4, messageId);
            statement.setString(5, body);
            statement.setTimestamp(6, createdAt);
            statement.executeUpdate();
            return new Enqueued(new OutboxMessage(id, messageId, "PENDING", 0), true);
        } catch (SQLException ex) {
            if (savepoint != null) {
                connection.rollback(savepoint);
            }
            if (isUniqueViolation(ex)) {
                return findMessage(appId, connectorId, messageId)
                        .map(row -> new Enqueued(row, false))
                        .orElseThrow(() -> new UncategorizedSQLException("outbox enqueue", ex.getSQLState(), ex));
            }
            throw ex;
        }
    }

    private static boolean isUniqueViolation(Throwable ex) {
        for (Throwable current = ex; current != null; current = current.getCause()) {
            if (current instanceof SQLException sql) {
                for (SQLException nested = sql; nested != null; nested = nested.getNextException()) {
                    if ("23505".equals(nested.getSQLState())) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    public Optional<OutboxMessage> findMessage(String appId, String connectorId, String messageId) {
        List<OutboxMessage> rows = jdbcTemplate.query("""
                SELECT id, message_id, status, attempts
                FROM public.application_outbox
                WHERE app_id = ? AND connector_id = ? AND message_id = ?
                """, (rs, row) -> new OutboxMessage(
                rs.getString(1),
                rs.getString(2),
                rs.getString(3),
                rs.getInt(4)
        ), appId, connectorId, messageId);
        return rows.stream().findFirst();
    }

    public boolean acceptInbox(String connectorId, String externalMessageId) {
        Integer existing = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM application_inbox WHERE connector_id = ? AND external_message_id = ?",
                Integer.class,
                connectorId,
                externalMessageId
        );
        if (existing != null && existing > 0) {
            return false;
        }
        jdbcTemplate.update(
                "INSERT INTO application_inbox (connector_id, external_message_id, received_at) VALUES (?, ?, ?)",
                connectorId,
                externalMessageId,
                Timestamp.from(Instant.now())
        );
        return true;
    }

    public int relayOnce(Delivery delivery) {
        return relayPending(null, delivery);
    }

    /**
     * Drains only the named connectors. An empty set delivers nothing, so a pump
     * for HTTP endpoints cannot dead-letter rows that belong to another transport.
     */
    public int relayOnce(Set<String> connectorIds, Delivery delivery) {
        if (connectorIds == null || connectorIds.isEmpty()) {
            return 0;
        }
        return relayPending(List.copyOf(connectorIds), delivery);
    }

    private int relayPending(Collection<String> connectorIds, Delivery delivery) {
        Timestamp now = Timestamp.from(Instant.now());
        List<Row> pending;
        if (connectorIds == null) {
            pending = jdbcTemplate.query("""
                    SELECT id, app_id, connector_id, message_id, body, attempts
                    FROM public.application_outbox
                    WHERE status = 'PENDING'
                      AND (claimed_until IS NULL OR claimed_until < ?)
                    ORDER BY created_at
                    """, rowMapper(), now);
        } else {
            String placeholders = String.join(",", java.util.Collections.nCopies(connectorIds.size(), "?"));
            Object[] args = new Object[connectorIds.size() + 1];
            int i = 0;
            for (String connectorId : connectorIds) {
                args[i++] = connectorId;
            }
            args[i] = now;
            pending = jdbcTemplate.query("""
                    SELECT id, app_id, connector_id, message_id, body, attempts
                    FROM public.application_outbox
                    WHERE status = 'PENDING'
                      AND connector_id IN (%s)
                      AND (claimed_until IS NULL OR claimed_until < ?)
                    ORDER BY created_at
                    """.formatted(placeholders), rowMapper(), args);
        }
        int sent = 0;
        for (Row row : pending) {
            if (!claimPending(row.id())) {
                continue;
            }
            try {
                delivery.deliver(row.appId(), row.connectorId(), row.messageId(), row.body());
                int marked = jdbcTemplate.update("""
                        UPDATE public.application_outbox
                        SET status = 'SENT', claimed_until = NULL
                        WHERE id = ? AND status = 'PENDING'
                        """, row.id());
                if (marked > 0) {
                    sent++;
                }
            } catch (RetryableOutboxDelivery ex) {
                jdbcTemplate.update("""
                        UPDATE public.application_outbox
                        SET claimed_until = NULL
                        WHERE id = ? AND status = 'PENDING'
                        """, row.id());
            } catch (RuntimeException ex) {
                jdbcTemplate.update("""
                        UPDATE public.application_outbox
                        SET attempts = attempts + 1,
                            status = CASE WHEN attempts + 1 >= ? THEN 'DLQ' ELSE 'PENDING' END,
                            claimed_until = NULL
                        WHERE id = ? AND status = 'PENDING'
                        """, DEAD_LETTER_AFTER, row.id());
            }
        }
        return sent;
    }

    private boolean claimPending(String id) {
        Timestamp now = Timestamp.from(Instant.now());
        Timestamp until = Timestamp.from(Instant.now().plus(CLAIM_LEASE));
        int claimed = jdbcTemplate.update("""
                UPDATE public.application_outbox
                SET claimed_until = ?
                WHERE id = ?
                  AND status = 'PENDING'
                  AND (claimed_until IS NULL OR claimed_until < ?)
                """, until, id, now);
        return claimed > 0;
    }

    private static org.springframework.jdbc.core.RowMapper<Row> rowMapper() {
        return (rs, row) -> new Row(
                rs.getString(1),
                rs.getString(2),
                rs.getString(3),
                rs.getString(4),
                rs.getString(5),
                rs.getInt(6)
        );
    }

    public String status(String id) {
        return jdbcTemplate.queryForObject(
                "SELECT status FROM public.application_outbox WHERE id = ?",
                String.class,
                id
        );
    }

    @FunctionalInterface
    public interface Delivery {
        void deliver(String appId, String connectorId, String messageId, String body);
    }

    public record OutboxMessage(String id, String messageId, String status, int attempts) {
    }

    public record Enqueued(OutboxMessage message, boolean created) {
        public String id() {
            return message.id();
        }
    }

    private record Row(String id, String appId, String connectorId, String messageId, String body, int attempts) {
    }
}
