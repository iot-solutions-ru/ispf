package com.ispf.server.cluster;

import com.ispf.server.config.ClusterProperties;
import com.ispf.server.config.NatsProperties;
import io.nats.client.Connection;
import io.nats.client.JetStream;
import io.nats.client.JetStreamApiException;
import io.nats.client.JetStreamManagement;
import io.nats.client.api.StreamInfo;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class NatsJetStreamPublishFailureTest {

    @Test
    void jetStreamPublishFailureIncrementsBridgeCounter() throws IOException, JetStreamApiException {
        NatsProperties properties = jetStreamEnabled();
        Connection connection = mock(Connection.class);
        JetStream jetStream = mock(JetStream.class);
        JetStreamManagement management = mock(JetStreamManagement.class);
        when(connection.jetStream()).thenReturn(jetStream);
        when(connection.jetStreamManagement()).thenReturn(management);
        when(management.getStreamInfo(anyString())).thenReturn(mock(StreamInfo.class));
        doThrow(new IOException("stream down")).when(jetStream).publish(anyString(), any(byte[].class));

        NatsEventBridge bridge = new NatsEventBridge(
                properties,
                mock(ClusterProperties.class),
                new ObjectMapper(),
                null,
                null,
                connection
        );
        NatsJetStreamSupport support = new NatsJetStreamSupport(properties, bridge);

        support.publishReplicaEvent("ispf.events.variable_updated", new byte[] {1});

        assertThat(bridge.publishFailureCount()).isEqualTo(1);
        assertThat(bridge.lastPublishError()).isEqualTo("stream down");
    }

    private static NatsProperties jetStreamEnabled() {
        return new NatsProperties(
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
                -1,
                30
        );
    }
}
