package com.ispf.server.audit;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabaseBuilder;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabaseType;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

import static org.junit.jupiter.api.Assertions.assertTrue;

class AuditChainStoreTest {

    private JdbcTemplate jdbc;
    private AuditChainStore store;
    private TransactionTemplate transactions;

    @BeforeEach
    void setUp() {
        var dataSource = new EmbeddedDatabaseBuilder().setType(EmbeddedDatabaseType.H2).build();
        jdbc = new JdbcTemplate(dataSource);
        store = new AuditChainStore(jdbc);
        store.ensureTable();
        jdbc.execute("""
                CREATE TABLE IF NOT EXISTS audit_events (
                    id VARCHAR(64) PRIMARY KEY,
                    category VARCHAR(64) NOT NULL,
                    action VARCHAR(128) NOT NULL,
                    actor VARCHAR(128),
                    target_type VARCHAR(64),
                    target_id VARCHAR(512),
                    details_json VARCHAR(4000),
                    occurred_at TIMESTAMP NOT NULL
                )
                """);
        PlatformTransactionManager txManager = new DataSourceTransactionManager(dataSource);
        transactions = new TransactionTemplate(txManager);
    }

    @Test
    void jdbcRoundTripSurvivesSubMicroPrecision() {
        Instant occurredAt = Instant.parse("2026-11-12T10:15:30.123456789Z");
        transactions.executeWithoutResult(status -> {
            insertEvent("e1", "{}", occurredAt);
            appendChain("e1", occurredAt);
        });
        assertTrue(store.verify());
    }

    private void insertEvent(String id, String details, Instant occurredAt) {
        jdbc.update("""
                INSERT INTO audit_events (id, category, action, actor, target_type, target_id, details_json, occurred_at)
                VALUES (?, 'object', 'update', 'op', 'var', 'path', ?, ?)
                """, id, details, java.sql.Timestamp.from(occurredAt.truncatedTo(ChronoUnit.MICROS)));
    }

    private void appendChain(String eventId, Instant occurredAt) {
        var event = new com.ispf.server.persistence.entity.AuditEventEntity();
        event.setId(eventId);
        event.setCategory("object");
        event.setAction("update");
        event.setActor("op");
        event.setTargetType("var");
        event.setTargetId("path");
        event.setDetailsJson("{}");
        event.setOccurredAt(occurredAt);
        store.append(event);
    }
}
