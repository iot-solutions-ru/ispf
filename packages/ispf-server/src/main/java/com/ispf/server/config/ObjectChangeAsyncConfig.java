package com.ispf.server.config;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

@Configuration
@EnableAsync
@ConditionalOnProperty(
        prefix = "ispf.object-change",
        name = "async-enabled",
        havingValue = "true",
        matchIfMissing = true
)
public class ObjectChangeAsyncConfig {

    @Bean(name = "objectChangeExecutor")
    ThreadPoolTaskExecutor objectChangeExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(2);
        executor.setMaxPoolSize(8);
        executor.setQueueCapacity(10_000);
        executor.setThreadNamePrefix("object-change-");
        executor.initialize();
        return executor;
    }
}
