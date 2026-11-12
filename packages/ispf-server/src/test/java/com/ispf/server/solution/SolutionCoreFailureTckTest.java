package com.ispf.server.solution;

import com.ispf.server.application.data.ApplicationMessageRelay;
import com.ispf.server.audit.AuditChain;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabaseBuilder;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabaseType;

import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Core guarantees a boxed solution and its plant fork get from the platform:
 * an edited audit row fails the chain, and an outage does not drop or duplicate a message.
 */
class SolutionCoreFailureTckTest {

    @Test
    void tamperedAuditRowFailsVerification() {
        Instant at = Instant.parse("2026-11-12T12:00:00Z");
        String first = AuditChain.hash(null, "1", "object", "write", "op", "var", "line", "before", at);
        String second = AuditChain.hash(first, "2", "object", "write", "op", "var", "line", "after", at);
        assertFalse(AuditChain.intact(List.of(
                new AuditChain.Link("1", "object", "write", "op", "var", "line", "tampered", at, null, first),
                new AuditChain.Link("2", "object", "write", "op", "var", "line", "after", at, first, second)
        )));
    }

    @Test
    void outboxSurvivesAReceiverOutageWithoutDuplicates() {
        JdbcTemplate jdbc = new JdbcTemplate(new EmbeddedDatabaseBuilder().setType(EmbeddedDatabaseType.H2).build());
        ApplicationMessageRelay relay = new ApplicationMessageRelay(jdbc);
        relay.ensureTables();
        String id = relay.enqueue("mes", "sap", "order-1", "{\"id\":1}");
        assertEquals(0, relay.relayOnce((app, connector, message, body) -> {
            throw new IllegalStateException("erp down");
        }));
        assertEquals(1, relay.relayOnce((app, connector, message, body) -> { }));
        assertEquals("SENT", relay.status(id));
        assertTrue(relay.acceptInbox("sap", "order-1"));
        assertFalse(relay.acceptInbox("sap", "order-1"));
    }
}
