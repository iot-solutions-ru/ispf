package com.ispf.server.history;

import com.ispf.server.config.AnalyticsProperties;
import com.ispf.server.platform.analytics.AnalyticsClusterWorkloadService;
import com.ispf.server.security.acl.VariableMemberAccessService;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Incrementally materializes historian rollups into ClickHouse (BL-205).
 */
@Service
public class HistorianRollupMaterializerService {

    private static final int MAX_BUCKETS_PER_TICK = 2_000;
    private static final Duration INITIAL_LOOKBACK = Duration.ofDays(30);

    private volatile HistorianRollupMaterializerService.TickResult lastTick =
            HistorianRollupMaterializerService.TickResult.skipped("not run yet");

    private final ClickHouseHistorianRollupStore rollupStore;
    private final HistorianRollupSubscriptionIndex subscriptionIndex;
    private final AnalyticsProperties analyticsProperties;
    private final AnalyticsClusterWorkloadService analyticsClusterWorkloadService;
    private final VariableMemberAccessService variableMemberAccessService;
    private final ConcurrentHashMap<String, Set<Instant>> dirtyHours = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, Set<Instant>> dirtyDays = new ConcurrentHashMap<>();

    public HistorianRollupMaterializerService(
            ClickHouseHistorianRollupStore rollupStore,
            HistorianRollupSubscriptionIndex subscriptionIndex,
            AnalyticsProperties analyticsProperties,
            AnalyticsClusterWorkloadService analyticsClusterWorkloadService,
            VariableMemberAccessService variableMemberAccessService
    ) {
        this.rollupStore = rollupStore;
        this.subscriptionIndex = subscriptionIndex;
        this.analyticsProperties = analyticsProperties;
        this.analyticsClusterWorkloadService = analyticsClusterWorkloadService;
        this.variableMemberAccessService = variableMemberAccessService;
    }

    public boolean isEnabled() {
        return analyticsProperties.materializerEnabled()
                && rollupStore.isConfigured()
                && analyticsClusterWorkloadService.isAnalyticsWorkloadActive();
    }

    public HistorianRollupMaterializerService.TickResult lastTick() {
        return lastTick;
    }

    public TickResult materializeTick() {
        if (!isEnabled()) {
            lastTick = TickResult.skipped("materializer disabled or ClickHouse not configured");
            return lastTick;
        }
        long started = System.nanoTime();
        Instant now = Instant.now();
        int subscriptions = 0;
        int bucketsWritten = 0;
        long maxLagMs = 0L;

        for (HistorianRollupSubscription subscription : subscriptionIndex.listSubscriptions()) {
            subscriptions++;
            bucketsWritten += materializeDirty(subscription);
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
        lastTick = new TickResult(true, subscriptions, bucketsWritten, maxLagMs, latencyMs, null);
        return lastTick;
    }

    public void noteLateSample(String objectPath, String variableName, String fieldName, Instant sampledAt) {
        if (!isEnabled() || sampledAt == null || objectPath == null || variableName == null) {
            return;
        }
        if ("$record".equals(fieldName)) {
            return;
        }
        String key = objectPath + "|" + variableName + "|" + (fieldName == null ? "value" : fieldName);
        markDirty(dirtyHours, key, sampledAt.truncatedTo(ChronoUnit.HOURS));
        markDirty(dirtyDays, key, sampledAt.truncatedTo(ChronoUnit.DAYS));
    }

    private static void markDirty(java.util.Map<String, Set<Instant>> dirty, String key, Instant bucket) {
        Set<Instant> buckets = dirty.computeIfAbsent(key, ignored -> ConcurrentHashMap.newKeySet());
        if (buckets.size() >= 512) {
            return;
        }
        buckets.add(bucket);
    }

    private int materializeDirty(HistorianRollupSubscription subscription) {
        String key = subscription.objectPath() + "|" + subscription.variableName() + "|" + subscription.fieldName();
        Set<Instant> late;
        if (Duration.ofHours(1).equals(subscription.bucket())) {
            late = dirtyHours.get(key);
        } else if (Duration.ofDays(1).equals(subscription.bucket())) {
            late = dirtyDays.get(key);
        } else {
            return 0;
        }
        if (late == null || late.isEmpty()) {
            return 0;
        }
        int written = 0;
        for (Instant start : Set.copyOf(late)) {
            Instant end = start.plus(subscription.bucket()).minusMillis(1);
            written += rollupStore.materializeRange(subscription, start, end, 8);
            late.remove(start);
        }
        return written;
    }

    public RebuildResult rebuild(
            String objectPath,
            String variableName,
            String fieldName,
            String bucketSpec,
            Instant from,
            Instant to,
            Authentication authentication
    ) {
        variableMemberAccessService.requireRead(objectPath, variableName, authentication);
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
