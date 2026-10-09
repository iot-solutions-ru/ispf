package com.ispf.server.cluster;

import com.ispf.server.config.NatsProperties;
import io.nats.client.Connection;
import io.nats.client.Dispatcher;
import io.nats.client.JetStream;
import io.nats.client.JetStreamManagement;
import io.nats.client.MessageHandler;
import io.nats.client.PushSubscribeOptions;
import org.junit.jupiter.api.Test;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.same;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class NatsJetStreamSupportTest {

    @Test
    void replicaSubscriptionLeavesAcknowledgementToTheHandler() throws Exception {
        NatsProperties properties = new NatsProperties(
                true,
                "nats://localhost:4222",
                true,
                "replica-1",
                true,
                "ispf-automation",
                24,
                "ispf-replica-",
                1024,
                false,
                1,
                1,
                50,
                6,
                30
        );
        NatsEventBridge bridge = mock(NatsEventBridge.class);
        Connection connection = mock(Connection.class);
        JetStream jetStream = mock(JetStream.class);
        when(bridge.connection()).thenReturn(connection);
        when(connection.jetStream()).thenReturn(jetStream);
        when(connection.jetStreamManagement()).thenReturn(mock(JetStreamManagement.class));
        Dispatcher dispatcher = mock(Dispatcher.class);
        MessageHandler handler = message -> {
        };

        new NatsJetStreamSupport(properties, bridge).subscribeReplicaEvents(dispatcher, handler);

        verify(jetStream).subscribe(
                eq(NatsJetStreamSupport.REPLICA_EVENTS_SUBJECT),
                same(dispatcher),
                same(handler),
                eq(false),
                any(PushSubscribeOptions.class)
        );
    }
}
