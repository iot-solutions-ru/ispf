package com.ispf.server.audit;

import com.ispf.server.persistence.entity.AuditEventEntity;
import com.ispf.server.relational.H2Dialect;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabaseBuilder;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabaseType;
import org.springframework.transaction.support.TransactionTemplate;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * ADR-0078 DoD: concurrent audit-chain appends keep the chain intact (H2 always-on gate).
 */
class AuditChainConcurrentAppendTest {

    private JdbcTemplate jdbc;
    private AuditChainStore store;
    private TransactionTemplate transactions;

    @BeforeEach
    void setUp() {
        var dataSource = new EmbeddedDatabaseBuilder()
                .setType(EmbeddedDatabaseType.H2)
                .setName("audit-chain-concurrent-" + UUID.randomUUID())
                .build();
        jdbc = new JdbcTemplate(dataSource);
        store = new AuditChainStore(jdbc, new H2Dialect());
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
        transactions = new TransactionTemplate(new DataSourceTransactionManager(dataSource));
    }

    @Test
    void parallelAppendsPreserveChain() throws Exception {
        int writers = 12;
        CountDownLatch start = new CountDownLatch(1);
        AtomicInteger ok = new AtomicInteger();
        ExecutorService pool = Executors.newFixedThreadPool(writers);
        List<Future<?>> futures = new ArrayList<>();
        for (int i = 0; i < writers; i++) {
            final int idx = i;
            futures.add(pool.submit(() -> {
                try {
                    start.await(10, TimeUnit.SECONDS);
                    transactions.executeWithoutResult(status -> {
                        String id = "e-" + idx + "-" + UUID.randomUUID();
                        Instant at = Instant.parse("2026-11-19T10:00:00Z").plusSeconds(idx);
                        jdbc.update("""
                                INSERT INTO audit_events
                                (id, category, action, actor, target_type, target_id, details_json, occurred_at)
                                VALUES (?, 'object', 'update', 'op', 'var', 'path', '{}', ?)
                                """, id, Timestamp.from(at));
                        AuditEventEntity event = new AuditEventEntity();
                        event.setId(id);
                        event.setCategory("object");
                        event.setAction("update");
                        event.setActor("op");
                        event.setTargetType("var");
                        event.setTargetId("path");
                        event.setDetailsJson("{}");
                        event.setOccurredAt(at);
                        store.append(event);
                    });
                    ok.incrementAndGet();
                } catch (Exception ex) {
                    throw new RuntimeException(ex);
                }
            }));
        }
        start.countDown();
        for (Future<?> future : futures) {
            future.get(30, TimeUnit.SECONDS);
        }
        pool.shutdownNow();
        assertEquals(writers, ok.get());
        assertTrue(store.verify());
        Integer rows = jdbc.queryForObject("SELECT COUNT(*) FROM audit_event_chain", Integer.class);
        assertEquals(writers, rows);
    }
}
