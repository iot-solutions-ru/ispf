package com.ispf.server.cluster;

import com.ispf.server.config.ClusterProperties;
import com.ispf.server.config.IspfRedisProperties;
import com.ispf.server.config.NatsProperties;
import io.nats.client.Connection;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.data.redis.connection.RedisConnection;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
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
                mockJs(true),
                redisTemplateThatPings("PONG")
        );
        assertNull(validator.findFailure());
    }

    @Test
    void failsWhenJetStreamStreamIsNotReady() {
        ClusterDependencyValidator validator = validator(
                cluster(true, false, false),
                nats(true, true),
                redis(false),
                mockBridge(mock(Connection.class)),
                mockJs(true, false)
        );
        String failure = validator.findFailure();
        assertNotNull(failure);
        assertTrue(failure.contains("ispf-automation"));
    }

    @Test
    void failsWhenPathInterestWithoutRedis() {
        ClusterDependencyValidator validator = validator(
                cluster(true, false, true),
                nats(true, true),
                redis(false),
                mockBridge(mock(Connection.class)),
                mockJs(true)
        );
        String failure = validator.findFailure();
        assertNotNull(failure);
        assertTrue(failure.contains("ispf.redis.enabled=true"));
    }

    @Test
    void failsWhenRedisEnabledButUnreachable() {
        IspfRedisProperties redis = redis(true);
        redis.setHost("redis");
        redis.setPort(6379);
        ClusterDependencyValidator validator = validator(
                cluster(true, true, false),
                nats(true, true),
                redis,
                mockBridge(mock(Connection.class)),
                mockJs(true),
                redisTemplateThatThrows(new RuntimeException("connection refused"))
        );
        String failure = validator.findFailure();
        assertNotNull(failure);
        assertTrue(failure.contains("not reachable at redis:6379"));
        assertTrue(failure.contains("connection refused"));
    }

    @Test
    void applicationRunnerThrowsIllegalStateException() {
        ClusterDependencyValidator validator = validator(
                cluster(true, false, false),
                nats(true, false),
                redis(false),
                mockBridge(mock(Connection.class)),
                mockJs(false)
        );
        IllegalStateException failure = assertThrows(IllegalStateException.class, validator::validateOrThrow);
        assertTrue(failure.getMessage().contains("jet-stream-enabled"));
    }

    private static ClusterDependencyValidator validator(
            ClusterProperties cluster,
            NatsProperties nats,
            IspfRedisProperties redis,
            NatsEventBridge bridge,
            NatsJetStreamSupport js
    ) {
        return validator(cluster, nats, redis, bridge, js, null);
    }

    private static ClusterDependencyValidator validator(
            ClusterProperties cluster,
            NatsProperties nats,
            IspfRedisProperties redis,
            NatsEventBridge bridge,
            NatsJetStreamSupport js,
            StringRedisTemplate template
    ) {
        @SuppressWarnings("unchecked")
        ObjectProvider<StringRedisTemplate> redisTemplate = mock(ObjectProvider.class);
        when(redisTemplate.getIfAvailable()).thenReturn(template);
        return new ClusterDependencyValidator(cluster, nats, redis, bridge, js, redisTemplate);
    }

    private static StringRedisTemplate redisTemplateThatPings(String pong) {
        StringRedisTemplate template = mock(StringRedisTemplate.class);
        RedisConnectionFactory factory = mock(RedisConnectionFactory.class);
        RedisConnection connection = mock(RedisConnection.class);
        when(template.getConnectionFactory()).thenReturn(factory);
        when(factory.getConnection()).thenReturn(connection);
        when(connection.ping()).thenReturn(pong);
        return template;
    }

    private static StringRedisTemplate redisTemplateThatThrows(RuntimeException failure) {
        StringRedisTemplate template = mock(StringRedisTemplate.class);
        RedisConnectionFactory factory = mock(RedisConnectionFactory.class);
        when(template.getConnectionFactory()).thenReturn(factory);
        when(factory.getConnection()).thenThrow(failure);
        return template;
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
                "edge-api",
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
        return mockJs(active, active);
    }

    private static NatsJetStreamSupport mockJs(boolean active, boolean streamReady) {
        NatsJetStreamSupport js = mock(NatsJetStreamSupport.class);
        when(js.isActive()).thenReturn(active);
        when(js.isStreamReady()).thenReturn(streamReady);
        return js;
    }
}
