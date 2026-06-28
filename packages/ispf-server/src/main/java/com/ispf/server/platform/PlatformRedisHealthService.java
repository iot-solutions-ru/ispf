package com.ispf.server.platform;

import com.ispf.server.config.IspfRedisProperties;
import com.ispf.server.correlator.CorrelatorWindowStore;
import com.ispf.server.correlator.RedisCorrelatorWindowStore;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.CacheManager;
import org.springframework.data.redis.cache.RedisCacheManager;
import org.springframework.data.redis.core.Cursor;
import org.springframework.data.redis.core.ScanOptions;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

@Service
public class PlatformRedisHealthService {

    private static final String CORRELATOR_KEY_PREFIX = "ispf:corr:hits:";

    private final IspfRedisProperties redisProperties;
    private final CorrelatorWindowStore correlatorWindowStore;
    private final CacheManager cacheManager;
    private final StringRedisTemplate redisTemplate;

    public PlatformRedisHealthService(
            IspfRedisProperties redisProperties,
            CorrelatorWindowStore correlatorWindowStore,
            CacheManager cacheManager,
            @Autowired(required = false) StringRedisTemplate redisTemplate
    ) {
        this.redisProperties = redisProperties;
        this.correlatorWindowStore = correlatorWindowStore;
        this.cacheManager = cacheManager;
        this.redisTemplate = redisTemplate;
    }

    public RedisHealth health() {
        boolean enabled = redisProperties.isEnabled();
        boolean connected = false;
        String connectionError = null;
        Long correlatorWindowKeys = null;

        if (enabled && redisTemplate != null) {
            try {
                String pong = redisTemplate.getConnectionFactory().getConnection().ping();
                connected = "PONG".equalsIgnoreCase(pong);
            } catch (Exception ex) {
                connectionError = ex.getMessage();
            }
            if (redisProperties.isCorrelatorWindowsEnabled() && connected) {
                correlatorWindowKeys = countCorrelatorWindowKeys();
            }
        }

        IspfRedisProperties.Cache cacheTtls = redisProperties.getCache();
        return new RedisHealth(
                enabled,
                connected,
                enabled ? redisProperties.getHost() : null,
                enabled ? redisProperties.getPort() : null,
                redisProperties.isCorrelatorWindowsEnabled(),
                correlatorWindowStore instanceof RedisCorrelatorWindowStore ? "redis" : "jdbc",
                cacheManager instanceof RedisCacheManager ? "redis" : "local",
                cacheTtls.getObjectAclTtl().toSeconds(),
                cacheTtls.getContextPackTtl().toSeconds(),
                cacheTtls.getPlatformBriefingTtl().toSeconds(),
                correlatorWindowKeys,
                connectionError
        );
    }

    private Long countCorrelatorWindowKeys() {
        try {
            ScanOptions options = ScanOptions.scanOptions()
                    .match(CORRELATOR_KEY_PREFIX + "*")
                    .count(256)
                    .build();
            long count = 0;
            try (Cursor<String> cursor = redisTemplate.scan(options)) {
                while (cursor.hasNext()) {
                    cursor.next();
                    count++;
                }
            }
            return count;
        } catch (Exception ex) {
            return null;
        }
    }

    public record RedisHealth(
            boolean enabled,
            boolean connected,
            String host,
            Integer port,
            boolean correlatorWindowsEnabled,
            String correlatorWindowStore,
            String aclCacheBackend,
            long objectAclTtlSeconds,
            long contextPackTtlSeconds,
            long platformBriefingTtlSeconds,
            Long correlatorWindowKeys,
            String connectionError
    ) {
    }
}
