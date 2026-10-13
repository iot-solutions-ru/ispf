package com.ispf.server.cluster;

import com.ispf.server.config.ClusterProperties;
import com.ispf.server.config.IspfRedisProperties;
import com.ispf.server.config.NatsProperties;
import io.nats.client.Connection;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ClusterDependencyValidatorTest {

    @Test
    void okWhenClusterDisabled() {
        ClusterDependencyValidator validator = validator(
                cluster(false, true, true),
                nats(true, true),
                redis(false),
                mockBridge(null),
                mockJs(false)
        );
        assertNull(validator.findFailure());
    }

    @Test
    void failsWhenClusterWithoutJetStreamFlag() {
        ClusterDependencyValidator validator = validator(
                cluster(true, true, true),
                nats(true, false),
                redis(true),
                mockBridge(mock(Connection.class)),
                mockJs(true)
        );
        assertNotNull(validator.findFailure());
    }

    @Test
    void failsWhenLiveMirrorWithoutRedis() {
        ClusterDependencyValidator validator = validator(
                cluster(true, true, true),
                nats(true, true),
                redis(false),
                mockBridge(mock(Connection.class)),
                mockJs(true)
        );
        assertNotNull(validator.findFailure());
    }

    @Test
    void okWhenClusterJetStreamAndRedisForMirror() {
        ClusterDependencyValidator validator = validator(
                cluster(true, true, true),
                nats(true, true),
                redis(true),
                mockBridge(mock(Connection.class)),
                mockJs(true)
        );
        assertNull(validator.findFailure());
    }

    private static ClusterDependencyValidator validator(
            ClusterProperties cluster,
            NatsProperties nats,
            IspfRedisProperties redis,
            NatsEventBridge bridge,
            NatsJetStreamSupport js
    ) {
        return new ClusterDependencyValidator(cluster, nats, redis, bridge, js);
    }

    private static ClusterProperties cluster(boolean enabled, boolean liveMirror, boolean pathInterest) {
        return new ClusterProperties(
                enabled,
                true,
                30,
                10,
                15,
                10,
                30,
                true,
                2,
                8,
                4,
                6,
                500,
                liveMirror,
                pathInterest,
                500,
                true,
                "unified",
                "",
                "all",
                true,
                2000,
                2,
                true,
                1,
                8,
                50,
                6,
                500,
                1800
        );
    }

    private static NatsProperties nats(boolean enabled, boolean jetStream) {
        return new NatsProperties(
                enabled,
                "nats://localhost:4222",
                true,
                "replica-test",
                jetStream,
                "ispf-automation",
                24,
                "ispf-replica-",
                65536,
                true,
                2,
                32,
                50,
                6,
                30
        );
    }

    private static IspfRedisProperties redis(boolean enabled) {
        IspfRedisProperties props = new IspfRedisProperties();
        props.setEnabled(enabled);
        return props;
    }

    private static NatsEventBridge mockBridge(Connection connection) {
        NatsEventBridge bridge = mock(NatsEventBridge.class);
        when(bridge.connection()).thenReturn(connection);
        return bridge;
    }

    private static NatsJetStreamSupport mockJs(boolean active) {
        NatsJetStreamSupport js = mock(NatsJetStreamSupport.class);
        when(js.isActive()).thenReturn(active);
        return js;
    }
}
