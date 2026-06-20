package com.ispf.server.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "ispf.nats")
public record NatsProperties(
        boolean enabled,
        String url,
        boolean replicaEventsEnabled,
        String replicaId
) {
    public NatsProperties {
        if (url == null || url.isBlank()) {
            url = "nats://localhost:4222";
        }
        if (replicaId == null || replicaId.isBlank()) {
            replicaId = java.util.UUID.randomUUID().toString();
        }
    }
}
