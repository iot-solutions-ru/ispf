package com.ispf.server.cluster;

import com.ispf.server.config.ClusterProperties;
import com.ispf.server.config.IspfRedisProperties;
import com.ispf.server.config.NatsProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

/**
 * ADR-0069 / tension Wave 1C: when cluster is enabled, refuse silent degradation.
 * Structure sync requires a connected NATS client, JetStream enabled, and a ready stream
 * ({@link NatsEventBridge} / {@link NatsObjectChangeSubscriber} otherwise fall back to core NATS).
 * Live mirror ({@link ClusterProperties#isLiveVariableSyncActive()}) and path interest
 * ({@link ClusterProperties#isClusterPathInterestActive()}) require Redis enabled and answering PING.
 *
 * <p>The {@code test} profile skips this runner so in-process suite contexts can enable cluster
 * mode without brokers. Predicate coverage is {@code ClusterDependencyValidatorTest}.
 */
@Component
@Profile("!test")
@Order(Ordered.HIGHEST_PRECEDENCE + 20)
public class ClusterDependencyValidator implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(ClusterDependencyValidator.class);

    private final ClusterProperties clusterProperties;
    private final NatsProperties natsProperties;
    private final IspfRedisProperties redisProperties;
    private final NatsEventBridge natsEventBridge;
    private final NatsJetStreamSupport jetStreamSupport;
    private final ObjectProvider<StringRedisTemplate> redisTemplate;

    public ClusterDependencyValidator(
            ClusterProperties clusterProperties,
            NatsProperties natsProperties,
            IspfRedisProperties redisProperties,
            NatsEventBridge natsEventBridge,
            NatsJetStreamSupport jetStreamSupport,
            ObjectProvider<StringRedisTemplate> redisTemplate
    ) {
        this.clusterProperties = clusterProperties;
        this.natsProperties = natsProperties;
        this.redisProperties = redisProperties;
        this.natsEventBridge = natsEventBridge;
        this.jetStreamSupport = jetStreamSupport;
        this.redisTemplate = redisTemplate;
    }

    @Override
    public void run(ApplicationArguments args) {
        validateOrThrow();
    }

    void validateOrThrow() {
        if (!clusterProperties.enabled()) {
            return;
        }
        String failure = findFailure();
        if (failure != null) {
            log.error("Cluster dependency check failed: {}", failure);
            throw new IllegalStateException(failure);
        }
        log.info(
                "Cluster dependency check OK (jetStream={}, redis={}, liveMirror={}, pathInterest={})",
                jetStreamSupport.isActive(),
                redisProperties.isEnabled(),
                clusterProperties.isLiveVariableSyncActive(),
                clusterProperties.isClusterPathInterestActive()
        );
    }

    /**
     * @return human-readable failure, or null when OK
     */
    String findFailure() {
        if (!clusterProperties.enabled()) {
            return null;
        }
        if (!natsProperties.enabled()) {
            return "ispf.cluster.enabled=true requires ispf.nats.enabled=true "
                    + "(structure sync / replica events)";
        }
        if (natsEventBridge.connection() == null) {
            return "ispf.cluster.enabled=true but NATS connection is unavailable "
                    + "(fail-closed; silent no-op bridge is not allowed)";
        }
        if (!natsProperties.jetStreamEnabled()) {
            return "ispf.cluster.enabled=true requires ispf.nats.jet-stream-enabled=true "
                    + "(JetStream for durable structure sync; see ADR-0069 / 0030)";
        }
        if (!jetStreamSupport.isActive()) {
            return "ispf.cluster.enabled=true but JetStream is not active "
                    + "(stream API unavailable; refuse core-NATS-only fallback)";
        }
        if (!jetStreamSupport.isStreamReady()) {
            return "ispf.cluster.enabled=true but JetStream stream '"
                    + natsProperties.jetStreamStreamName()
                    + "' is not ready (refuse core-NATS fallback)";
        }
        if (redisRequired()) {
            if (!redisProperties.isEnabled()) {
                return "cluster live variable mirror / path interest requires ispf.redis.enabled=true "
                        + "(ADR-0029 / 0069 fail-closed)";
            }
            String redisFailure = redisUnreachable();
            if (redisFailure != null) {
                return redisFailure;
            }
        }
        return null;
    }

    private boolean redisRequired() {
        return clusterProperties.isLiveVariableSyncActive() || clusterProperties.isClusterPathInterestActive();
    }

    private String redisUnreachable() {
        String target = redisProperties.getHost() + ":" + redisProperties.getPort();
        StringRedisTemplate template = redisTemplate.getIfAvailable();
        if (template == null || template.getConnectionFactory() == null) {
            return "Redis is enabled but not reachable at " + target + " (client is not configured)";
        }
        try {
            String pong = template.getConnectionFactory().getConnection().ping();
            if ("PONG".equalsIgnoreCase(pong)) {
                return null;
            }
            return "Redis is enabled but not reachable at " + target + " (PING returned " + pong + ")";
        } catch (RuntimeException ex) {
            log.warn("Cluster Redis ping failed for {}: {}", target, ex.getMessage());
            String detail = ex.getMessage() == null ? ex.getClass().getSimpleName() : ex.getMessage();
            return "Redis is enabled but not reachable at " + target + " (" + detail + ")";
        }
    }
}
