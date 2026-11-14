package com.ispf.server.application.data;

import com.ispf.server.cluster.NatsJetStreamSupport;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabaseBuilder;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabaseType;

import java.nio.charset.StandardCharsets;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class NatsOutboxDeliveryTest {

    @Mock
    private NatsJetStreamSupport jetStream;

    @Test
    void outageKeepsTheRowUntilJetStreamAcceptsIt() {
        doThrow(new NatsJetStreamSupport.OutboxPublishException("broker down"))
                .doNothing()
                .when(jetStream)
                .publishOutbox(eq("erp-nats"), eq("m-nats"), any());

        JdbcTemplate jdbc = new JdbcTemplate(new EmbeddedDatabaseBuilder().setType(EmbeddedDatabaseType.H2).build());
        ApplicationMessageRelay relay = new ApplicationMessageRelay(jdbc);
        relay.ensureTables();
        String id = relay.enqueue("plant", "erp-nats", "m-nats", "{\"qty\":1}");
        NatsOutboxDelivery delivery = new NatsOutboxDelivery(jetStream, Set.of("erp-nats"));

        assertEquals(0, relay.relayOnce(delivery.connectors(), delivery));
        assertEquals("PENDING", relay.status(id));

        assertEquals(1, relay.relayOnce(delivery.connectors(), delivery));
        assertEquals("SENT", relay.status(id));
        verify(jetStream, times(2)).publishOutbox(
                eq("erp-nats"),
                eq("m-nats"),
                eq("{\"qty\":1}".getBytes(StandardCharsets.UTF_8))
        );
    }

    @Test
    void unknownConnectorIsRejected() {
        NatsOutboxDelivery delivery = new NatsOutboxDelivery(jetStream, Set.of("erp-nats"));
        assertThrows(IllegalStateException.class, () -> delivery.deliver("plant", "other", "m1", "{}"));
    }
}
