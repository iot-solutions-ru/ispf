package com.ispf.server.application.data;

import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabaseBuilder;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabaseType;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ApplicationMessageRelayTest {

    @Test
    void outageKeepsTheMessageAndADuplicateInboxIsIgnored() {
        JdbcTemplate jdbc = new JdbcTemplate(new EmbeddedDatabaseBuilder().setType(EmbeddedDatabaseType.H2).build());
        ApplicationMessageRelay relay = new ApplicationMessageRelay(jdbc);
        relay.ensureTables();
        String id = relay.enqueue("plant", "sap", "m-1", "{\"qty\":1}");
        assertEquals(0, relay.relayOnce((app, connector, message, body) -> {
            throw new IllegalStateException("down");
        }));
        assertEquals("PENDING", relay.status(id));
        assertEquals(1, relay.relayOnce((app, connector, message, body) -> { }));
        assertEquals("SENT", relay.status(id));
        assertTrue(relay.acceptInbox("sap", "ext-1"));
        assertFalse(relay.acceptInbox("sap", "ext-1"));
    }

    @Test
    void fifthFailureMovesTheRowToTheDeadLetter() {
        JdbcTemplate jdbc = new JdbcTemplate(new EmbeddedDatabaseBuilder().setType(EmbeddedDatabaseType.H2).build());
        ApplicationMessageRelay relay = new ApplicationMessageRelay(jdbc);
        relay.ensureTables();
        String id = relay.enqueue("plant", "sap", "m-2", "{}");
        for (int i = 0; i < 5; i++) {
            relay.relayOnce((app, connector, message, body) -> {
                throw new IllegalStateException("down");
            });
        }
        assertEquals("DLQ", relay.status(id));
    }

    @Test
    void receiverOutageStaysPendingWithoutDeadLetter() {
        JdbcTemplate jdbc = new JdbcTemplate(new EmbeddedDatabaseBuilder().setType(EmbeddedDatabaseType.H2).build());
        ApplicationMessageRelay relay = new ApplicationMessageRelay(jdbc);
        relay.ensureTables();
        String id = relay.enqueue("plant", "sap", "m-outage", "{}");
        for (int i = 0; i < 30; i++) {
            assertEquals(0, relay.relayOnce((app, connector, message, body) -> {
                throw new RetryableOutboxDelivery("down");
            }));
        }
        assertEquals("PENDING", relay.status(id));
        assertEquals(1, relay.relayOnce((app, connector, message, body) -> { }));
        assertEquals("SENT", relay.status(id));
    }
}
