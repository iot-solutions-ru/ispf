package com.ispf.server.config;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.cache.CacheManager;
import org.springframework.cache.concurrent.ConcurrentMapCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.cache.annotation.EnableCaching;

@Configuration
@EnableCaching
@ConditionalOnProperty(prefix = "ispf.redis", name = "enabled", havingValue = "false", matchIfMissing = true)
public class LocalCacheConfig {

    @Bean
    CacheManager cacheManager() {
        ConcurrentMapCacheManager manager = new ConcurrentMapCacheManager(
                "contextPack",
                "platformBriefing",
                "objectAcl"
        );
        manager.setAllowNullValues(false);
        return manager;
    }
}
