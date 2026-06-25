package com.ispf.server.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "ispf.nats")
public record NatsProperties(
        boolean enabled,
        String url,
        boolean replicaEventsEnabled,
        String replicaId,
        boolean jetStreamEnabled,
        String jetStreamStreamName,
        int jetStreamMaxAgeHours,
        String jetStreamReplicaConsumerPrefix
) {
    public NatsProperties {
        if (url == null || url.isBlank()) {
            url = "nats://localhost:4222";
        }
        if (replicaId == null || replicaId.isBlank()) {
            replicaId = java.util.UUID.randomUUID().toString();
        }
        if (jetStreamStreamName == null || jetStreamStreamName.isBlank()) {
            jetStreamStreamName = "ispf-automation";
        }
        if (jetStreamMaxAgeHours <= 0) {
            jetStreamMaxAgeHours = 24;
        }
        if (jetStreamReplicaConsumerPrefix == null || jetStreamReplicaConsumerPrefix.isBlank()) {
            jetStreamReplicaConsumerPrefix = "ispf-replica-";
        }
    }
}
