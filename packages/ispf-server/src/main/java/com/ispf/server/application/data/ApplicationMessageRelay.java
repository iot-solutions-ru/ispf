package com.ispf.server.application.data;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Transactional outbox and idempotent inbox for any {@code app_*} connector.
 * A delivery attempt that throws stays pending. After five failures the row is dead-lettered.
 */
@Service
public class ApplicationMessageRelay {

    static final int DEAD_LETTER_AFTER = 5;

    private final JdbcTemplate jdbcTemplate;

    public ApplicationMessageRelay(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    /**
     * Idempotent DDL for unit tests and profiles without Flyway. Production schema is V96.
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
                CREATE TABLE IF NOT EXISTS application_inbox (
                    connector_id VARCHAR(128) NOT NULL,
                    external_message_id VARCHAR(256) NOT NULL,
                    received_at TIMESTAMP NOT NULL,
                    PRIMARY KEY (connector_id, external_message_id)
                )
                """);
    }

    public String enqueue(String appId, String connectorId, String messageId, String body) {
        String id = UUID.randomUUID().toString();
        jdbcTemplate.update("""
                INSERT INTO application_outbox (id, app_id, connector_id, message_id, body, status, attempts, created_at)
                VALUES (?, ?, ?, ?, ?, 'PENDING', 0, ?)
                """, id, appId, connectorId, messageId, body, Timestamp.from(Instant.now()));
        return id;
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
        List<Row> pending;
        if (connectorIds == null) {
            pending = jdbcTemplate.query("""
                    SELECT id, app_id, connector_id, message_id, body, attempts
                    FROM application_outbox
                    WHERE status = 'PENDING'
                    ORDER BY created_at
                    """, rowMapper());
        } else {
            String placeholders = String.join(",", java.util.Collections.nCopies(connectorIds.size(), "?"));
            pending = jdbcTemplate.query("""
                    SELECT id, app_id, connector_id, message_id, body, attempts
                    FROM application_outbox
                    WHERE status = 'PENDING' AND connector_id IN (%s)
                    ORDER BY created_at
                    """.formatted(placeholders), rowMapper(), connectorIds.toArray());
        }
        int sent = 0;
        for (Row row : pending) {
            try {
                delivery.deliver(row.appId(), row.connectorId(), row.messageId(), row.body());
                jdbcTemplate.update("UPDATE application_outbox SET status = 'SENT' WHERE id = ?", row.id());
                sent++;
            } catch (RetryableOutboxDelivery ex) {
                jdbcTemplate.update(
                        "UPDATE application_outbox SET status = 'PENDING' WHERE id = ?",
                        row.id()
                );
            } catch (RuntimeException ex) {
                int attempts = row.attempts() + 1;
                String status = attempts >= DEAD_LETTER_AFTER ? "DLQ" : "PENDING";
                jdbcTemplate.update(
                        "UPDATE application_outbox SET attempts = ?, status = ? WHERE id = ?",
                        attempts,
                        status,
                        row.id()
                );
            }
        }
        return sent;
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
                "SELECT status FROM application_outbox WHERE id = ?",
                String.class,
                id
        );
    }

    @FunctionalInterface
    public interface Delivery {
        void deliver(String appId, String connectorId, String messageId, String body);
    }

    private record Row(String id, String appId, String connectorId, String messageId, String body, int attempts) {
    }
}
