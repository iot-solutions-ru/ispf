package com.ispf.server.config;

import com.ispf.driver.ingress.IngressElasticSettings;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

@ConfigurationProperties(prefix = "ispf.nats")
public record NatsProperties(
        boolean enabled,
        String url,
        boolean replicaEventsEnabled,
        String replicaId,
        boolean jetStreamEnabled,
        String jetStreamStreamName,
        int jetStreamMaxAgeHours,
        String jetStreamReplicaConsumerPrefix,
        @DefaultValue("65536") int replicaConsumerQueueCapacity,
        @DefaultValue("true") boolean replicaConsumerElasticEnabled,
        @DefaultValue("2") int replicaConsumerWorkerThreadsMin,
        @DefaultValue("32") int replicaConsumerWorkerThreadsMax,
        @DefaultValue("50") int replicaConsumerElasticScaleUpThreshold,
        @DefaultValue("6") int replicaConsumerElasticScaleDownSteps,
        @DefaultValue("30") int slowConsumerLogIntervalSeconds
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

    public IngressElasticSettings resolvedReplicaConsumerElastic() {
        int max = Math.max(1, replicaConsumerWorkerThreadsMax);
        int min = replicaConsumerElasticEnabled ? Math.max(1, replicaConsumerWorkerThreadsMin) : max;
        return new IngressElasticSettings(
                replicaConsumerElasticEnabled,
                Math.min(min, max),
                max,
                replicaConsumerElasticScaleUpThreshold,
                replicaConsumerElasticScaleDownSteps,
                500
        );
    }
}
