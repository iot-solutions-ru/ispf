package com.ispf.driver.ingress;

import java.util.Map;

/**
 * Elastic worker tuning for driver ingress lanes (L0 MQTT, L1 server buffer).
 *
 * @param scaleCheckIntervalMs retained for config compatibility; scaling is event-driven, interval is ignored
 */
public record IngressElasticSettings(
        boolean enabled,
        int minWorkers,
        int maxWorkers,
        int scaleUpQueueThreshold,
        int scaleDownSteps,
        int scaleCheckIntervalMs
) {

    public IngressElasticSettings {
        minWorkers = Math.max(1, minWorkers);
        maxWorkers = Math.max(minWorkers, maxWorkers);
        scaleUpQueueThreshold = Math.max(1, scaleUpQueueThreshold);
        scaleDownSteps = Math.max(1, scaleDownSteps);
        scaleCheckIntervalMs = Math.max(50, scaleCheckIntervalMs);
    }

    public static IngressElasticSettings fixed(int workers) {
        int threads = Math.max(1, workers);
        return new IngressElasticSettings(false, threads, threads, 100, 6, 200);
    }

    public int resolvedMinWorkers() {
        return enabled ? minWorkers : maxWorkers;
    }

    public int resolvedMaxWorkers() {
        return enabled ? maxWorkers : maxWorkers;
    }

    public static IngressElasticSettings resolve(
            Map<String, String> configuration,
            IngressElasticSettings platformDefaults
    ) {
        boolean elastic = DriverIngress.resolveBoolean(
                configuration,
                DriverIngress.CALLBACK_ELASTIC_ENABLED,
                platformDefaults.enabled()
        );
        int fixedThreads = DriverIngress.resolveThreads(configuration, platformDefaults.maxWorkers());
        int min = elastic
                ? DriverIngress.resolvePositive(
                configuration,
                DriverIngress.CALLBACK_THREADS_MIN,
                platformDefaults.minWorkers()
        )
                : fixedThreads;
        int max = elastic
                ? DriverIngress.resolvePositive(
                configuration,
                DriverIngress.CALLBACK_THREADS_MAX,
                platformDefaults.maxWorkers()
        )
                : fixedThreads;
        int scaleUp = DriverIngress.resolvePositive(
                configuration,
                DriverIngress.CALLBACK_SCALE_UP_QUEUE_THRESHOLD,
                platformDefaults.scaleUpQueueThreshold()
        );
        int scaleDown = DriverIngress.resolvePositive(
                configuration,
                DriverIngress.CALLBACK_SCALE_DOWN_STEPS,
                platformDefaults.scaleDownSteps()
        );
        int scaleCheckMs = DriverIngress.resolvePositive(
                configuration,
                DriverIngress.CALLBACK_SCALE_CHECK_INTERVAL_MS,
                platformDefaults.scaleCheckIntervalMs()
        );
        return new IngressElasticSettings(elastic, min, max, scaleUp, scaleDown, scaleCheckMs);
    }
}
