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
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AuditChainStoreTest {

    private JdbcTemplate jdbc;
    private AuditChainStore store;
    private TransactionTemplate transactions;

    @BeforeEach
    void setUp() {
        var dataSource = new EmbeddedDatabaseBuilder()
                .setType(EmbeddedDatabaseType.H2)
                .setName("audit-chain-" + UUID.randomUUID())
                .build();
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

    @Test
    void verifyFailsWhenChainRowHasNoAuditEvent() {
        Instant occurredAt = Instant.parse("2026-11-12T10:15:30Z");
        transactions.executeWithoutResult(status -> {
            jdbc.update("""
                    INSERT INTO audit_event_chain (event_id, previous_hash, chain_hash, occurred_at)
                    VALUES (?, NULL, ?, ?)
                    """, "orphan", "deadbeef", java.sql.Timestamp.from(occurredAt));
        });
        assertFalse(store.verify());
    }

    @Test
    void chainHeadFollowsAppendOrderNotOccurredAt() {
        Instant early = Instant.parse("2026-11-12T08:00:00Z");
        Instant late = Instant.parse("2026-11-12T12:00:00Z");
        transactions.executeWithoutResult(status -> {
            insertEvent("first", "{}", early);
            appendChain("first", early);
            insertEvent("second", "{}", late);
            appendChain("second", late);
            insertEvent("third", "{}", early);
            appendChain("third", early);
        });
        assertTrue(store.verify());
        String tailEventId = jdbc.queryForObject("""
                SELECT event_id FROM audit_event_chain ORDER BY seq DESC LIMIT 1
                """, String.class);
        org.junit.jupiter.api.Assertions.assertEquals("third", tailEventId);
    }

    private void insertEvent(String id, String details, Instant occurredAt) {
        Instant persistedAt = occurredAt.truncatedTo(ChronoUnit.MICROS);
        jdbc.update("""
                INSERT INTO audit_events (id, category, action, actor, target_type, target_id, details_json, occurred_at)
                VALUES (?, 'object', 'update', 'op', 'var', 'path', ?, ?)
                """, id, details, java.sql.Timestamp.from(persistedAt));
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
