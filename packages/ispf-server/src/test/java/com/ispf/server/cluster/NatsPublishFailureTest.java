package com.ispf.server.cluster;

import com.ispf.server.config.ClusterProperties;
import com.ispf.server.config.NatsProperties;
import com.ispf.server.platform.PlatformNatsHealthService;
import io.nats.client.Connection;
import io.nats.client.Connection.Status;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.lenient;

class NatsPublishFailureTest {

    @Test
    void publishFailureIsCountedAndRemembered() {
        Connection connection = mock(Connection.class);
        doThrow(new IllegalStateException("broken pipe"))
                .when(connection)
                .publish(anyString(), any(byte[].class));
        NatsEventBridge bridge = bridge(connection);

        bridge.publish("ispf.test", "hello");

        assertThat(bridge.publishFailureCount()).isEqualTo(1);
        assertThat(bridge.lastPublishError()).isEqualTo("broken pipe");
    }

    @Test
    void workflowPublishFailsWhenClientIsNotConnected() {
        Connection connection = mock(Connection.class);
        when(connection.getStatus()).thenReturn(Status.RECONNECTING);
        NatsEventBridge bridge = new NatsEventBridge(
                enabledProperties(),
                mock(ClusterProperties.class),
                new ObjectMapper(),
                null,
                null,
                connection
        );

        assertThat(bridge.isEnabled()).isFalse();
        assertThatThrownBy(() -> bridge.publishWorkflowStep("ispf.workflow.event", "payload"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("NATS publish failed");
        assertThat(bridge.publishFailureCount()).isEqualTo(1);
    }

    @Test
    void healthShowsThePublishFailure() {
        NatsEventBridge bridge = mock(NatsEventBridge.class);
        when(bridge.isEnabled()).thenReturn(true);
        when(bridge.publishFailureCount()).thenReturn(1L);
        when(bridge.lastPublishError()).thenReturn("broken pipe");
        PlatformNatsHealthService health = new PlatformNatsHealthService(
                enabledProperties(),
                bridge,
                mock(NatsJetStreamSupport.class)
        );

        PlatformNatsHealthService.NatsHealth snapshot = health.health();
        assertThat(snapshot.connected()).isTrue();
        assertThat(snapshot.publishFailureCount()).isEqualTo(1);
        assertThat(snapshot.lastPublishError()).isEqualTo("broken pipe");
    }

    private static NatsEventBridge bridge(Connection connection) {
        lenient().when(connection.getStatus()).thenReturn(Status.CONNECTED);
        return new NatsEventBridge(
                enabledProperties(),
                mock(ClusterProperties.class),
                new ObjectMapper(),
                null,
                null,
                connection
        );
    }

    private static NatsProperties enabledProperties() {
        return new NatsProperties(
                true,
                "nats://127.0.0.1:1",
                false,
                "replica-1",
                false,
                "ispf-automation",
                24,
                "ispf-replica-",
                8,
                false,
                1,
                1,
                1,
                1,
                -1, 30
        );
    }
}
