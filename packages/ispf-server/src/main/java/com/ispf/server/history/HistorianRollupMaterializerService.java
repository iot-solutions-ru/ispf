package com.ispf.server.history;

import com.ispf.server.config.AnalyticsProperties;
import com.ispf.server.platform.analytics.AnalyticsClusterWorkloadService;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;

/**
 * Incrementally materializes historian rollups into ClickHouse (BL-205).
 */
@Service
public class HistorianRollupMaterializerService {

    private static final int MAX_BUCKETS_PER_TICK = 2_000;
    private static final Duration INITIAL_LOOKBACK = Duration.ofDays(30);

    private final ClickHouseHistorianRollupStore rollupStore;
    private final HistorianRollupSubscriptionIndex subscriptionIndex;
    private final AnalyticsProperties analyticsProperties;
    private final AnalyticsClusterWorkloadService analyticsClusterWorkloadService;

    public HistorianRollupMaterializerService(
            ClickHouseHistorianRollupStore rollupStore,
            HistorianRollupSubscriptionIndex subscriptionIndex,
            AnalyticsProperties analyticsProperties,
            AnalyticsClusterWorkloadService analyticsClusterWorkloadService
    ) {
        this.rollupStore = rollupStore;
        this.subscriptionIndex = subscriptionIndex;
        this.analyticsProperties = analyticsProperties;
        this.analyticsClusterWorkloadService = analyticsClusterWorkloadService;
    }

    public boolean isEnabled() {
        return analyticsProperties.materializerEnabled()
                && rollupStore.isConfigured()
                && analyticsClusterWorkloadService.isAnalyticsWorkloadActive();
    }

    public TickResult materializeTick() {
        if (!isEnabled()) {
            return TickResult.skipped("materializer disabled or ClickHouse not configured");
        }
        long started = System.nanoTime();
        Instant now = Instant.now();
        int subscriptions = 0;
        int bucketsWritten = 0;
        long maxLagMs = 0L;

        for (HistorianRollupSubscription subscription : subscriptionIndex.listSubscriptions()) {
            subscriptions++;
            Instant from = rollupStore.maxMaterializedBucketStart(
                    subscription.objectPath(),
                    subscription.variableName(),
                    subscription.fieldName(),
                    subscription.bucket()
            ).orElse(now.minus(INITIAL_LOOKBACK));
            if (from.isAfter(now)) {
                from = now.minus(subscription.bucket());
            }
            int written = rollupStore.materializeRange(subscription, from, now, MAX_BUCKETS_PER_TICK);
            bucketsWritten += written;
            if (written > 0) {
                long lagMs = Duration.between(from, now).toMillis();
                maxLagMs = Math.max(maxLagMs, lagMs);
            }
        }

        long latencyMs = (System.nanoTime() - started) / 1_000_000L;
        return new TickResult(true, subscriptions, bucketsWritten, maxLagMs, latencyMs, null);
    }

    public RebuildResult rebuild(
            String objectPath,
            String variableName,
            String fieldName,
            String bucketSpec,
            Instant from,
            Instant to
    ) {
        if (!rollupStore.isConfigured()) {
            throw new IllegalStateException("ClickHouse historian rollups are not configured");
        }
        if (!analyticsClusterWorkloadService.isAnalyticsWorkloadActive()) {
            throw new IllegalStateException(
                    "Historian rollup rebuild is not active on this replica (dedicated analytics replicas exist)");
        }
        Duration bucket = VariableHistoryService.parseBucket(bucketSpec);
        HistorianRollupSubscription subscription = new HistorianRollupSubscription(
                objectPath,
                variableName,
                fieldName,
                bucket
        );
        rollupStore.deleteRange(subscription, from, to);
        int written = rollupStore.materializeRange(subscription, from, to, MAX_BUCKETS_PER_TICK);
        return new RebuildResult(objectPath, variableName, fieldName, bucketSpec, written);
    }

    public record TickResult(
            boolean ran,
            int subscriptions,
            int bucketsWritten,
            long maxLagMs,
            long latencyMs,
            String skipReason
    ) {
        static TickResult skipped(String reason) {
            return new TickResult(false, 0, 0, 0L, 0L, reason);
        }
    }

    public record RebuildResult(
            String objectPath,
            String variableName,
            String field,
            String bucket,
            int bucketsWritten
    ) {
    }
}
