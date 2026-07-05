package com.ispf.server.object.pubsub;

import com.ispf.server.config.ClusterProperties;
import com.ispf.server.config.IspfRedisProperties;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

/**
 * ADR-0029: Redis ref-counts for WS path subscriptions across cluster replicas.
 */
@Component
@ConditionalOnProperty(prefix = "ispf.redis", name = "enabled", havingValue = "true")
@ConditionalOnProperty(prefix = "ispf.cluster", name = "cluster-path-interest-enabled", havingValue = "true", matchIfMissing = true)
public class RedisClusterPathInterestStore implements ClusterPathInterestStore {

    private static final String BROADCAST_KEY = "ispf:cluster:ws:broadcast";
    private static final String PATH_PREFIX = "ispf:cluster:ws:interest:";

    private final ClusterProperties clusterProperties;
    private final StringRedisTemplate redis;

    public RedisClusterPathInterestStore(
            ClusterProperties clusterProperties,
            StringRedisTemplate redis,
            IspfRedisProperties redisProperties
    ) {
        this.clusterProperties = clusterProperties;
        this.redis = redis;
    }

    @Override
    public void onBroadcastSessionAdded() {
        if (!active()) {
            return;
        }
        redis.opsForValue().increment(BROADCAST_KEY);
    }

    @Override
    public void onBroadcastSessionRemoved() {
        if (!active()) {
            return;
        }
        decrement(BROADCAST_KEY);
    }

    @Override
    public void subscribePath(String path) {
        if (!active() || path == null || path.isBlank()) {
            return;
        }
        for (String prefix : pathPrefixes(path)) {
            redis.opsForValue().increment(PATH_PREFIX + prefix);
        }
    }

    @Override
    public void unsubscribePath(String path) {
        if (!active() || path == null || path.isBlank()) {
            return;
        }
        for (String prefix : pathPrefixes(path)) {
            decrement(PATH_PREFIX + prefix);
        }
    }

    @Override
    public boolean hasPathInterest(String eventPath) {
        if (!active()) {
            return false;
        }
        if (eventPath == null || eventPath.isBlank()) {
            return count(BROADCAST_KEY) > 0;
        }
        if (count(BROADCAST_KEY) > 0) {
            return true;
        }
        String prefix = "";
        for (String part : eventPath.split("\\.")) {
            prefix = prefix.isEmpty() ? part : prefix + "." + part;
            if (count(PATH_PREFIX + prefix) > 0) {
                return true;
            }
        }
        return false;
    }

    private boolean active() {
        return clusterProperties.isClusterPathInterestActive();
    }

    private long count(String key) {
        String raw = redis.opsForValue().get(key);
        if (raw == null || raw.isBlank()) {
            return 0L;
        }
        try {
            return Long.parseLong(raw);
        } catch (NumberFormatException ex) {
            return 0L;
        }
    }

    private void decrement(String key) {
        Long next = redis.opsForValue().decrement(key);
        if (next != null && next <= 0) {
            redis.delete(key);
        }
    }

    private static Iterable<String> pathPrefixes(String path) {
        java.util.List<String> prefixes = new java.util.ArrayList<>();
        String prefix = "";
        for (String part : path.split("\\.")) {
            prefix = prefix.isEmpty() ? part : prefix + "." + part;
            prefixes.add(prefix);
        }
        return prefixes;
    }
}
