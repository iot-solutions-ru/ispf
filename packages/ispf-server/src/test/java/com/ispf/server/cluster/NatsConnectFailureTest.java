package com.ispf.server.cluster;

import com.ispf.server.config.ClusterProperties;
import com.ispf.server.config.NatsProperties;
import com.ispf.server.platform.PlatformNatsHealthService;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class NatsConnectFailureTest {

    @Test
    void enabledNatsWithoutAServerKeepsTheConnectionError() {
        NatsEventBridge bridge = new NatsEventBridge(
                enabledClosedPort(),
                mock(ClusterProperties.class),
                new ObjectMapper(),
                null,
                null
        );

        assertThat(bridge.connection()).isNull();
        assertThat(bridge.isEnabled()).isFalse();
        assertThat(bridge.connectFailure()).isNotBlank();

        bridge.publish("ispf.workflow.demo.started", "payload");
        assertThat(bridge.connectFailure()).doesNotContain("no-op");
    }

    @Test
    void healthReportsTheConnectFailureInsteadOfADisabledBridge() {
        NatsEventBridge bridge = mock(NatsEventBridge.class);
        when(bridge.isEnabled()).thenReturn(false);
        when(bridge.connectFailure()).thenReturn("Connection refused");
        PlatformNatsHealthService health = new PlatformNatsHealthService(
                enabledClosedPort(),
                bridge,
                mock(NatsJetStreamSupport.class)
        );

        PlatformNatsHealthService.NatsHealth snapshot = health.health();
        assertThat(snapshot.enabled()).isTrue();
        assertThat(snapshot.connected()).isFalse();
        assertThat(snapshot.publishNatsAvailable()).isFalse();
        assertThat(snapshot.connectionError()).isEqualTo("Connection refused");
    }

    private static NatsProperties enabledClosedPort() {
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
                30
        );
    }
}
