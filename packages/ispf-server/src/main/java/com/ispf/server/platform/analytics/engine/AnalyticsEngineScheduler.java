package com.ispf.server.platform.analytics.engine;

import com.ispf.analytics.engine.AnalyticsTagDefinition;
import com.ispf.server.config.AnalyticsProperties;
import com.ispf.server.config.ClusterProperties;
import com.ispf.server.platform.PlatformLeaderLockService;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Leader-elected periodic analytics engine scheduler on a dedicated thread pool (BL-203).
 */
@Component
public class AnalyticsEngineScheduler {

    private static final String LOCK_NAME = "analytics_engine_scheduler";
    private static final Duration LOCK_TTL = Duration.ofSeconds(30);

    private final AnalyticsEngineService engineService;
    private final AnalyticsTagCatalogService catalogService;
    private final AnalyticsScheduleRegistry scheduleRegistry;
    private final PlatformLeaderLockService leaderLockService;
    private final ClusterProperties clusterProperties;
    private final AnalyticsProperties analyticsProperties;
    private final ThreadPoolTaskScheduler taskScheduler = new ThreadPoolTaskScheduler();
    private final AtomicReference<ScheduledFuture<?>> wakeTask = new AtomicReference<>();

    public AnalyticsEngineScheduler(
            AnalyticsEngineService engineService,
            AnalyticsTagCatalogService catalogService,
            AnalyticsScheduleRegistry scheduleRegistry,
            PlatformLeaderLockService leaderLockService,
            ClusterProperties clusterProperties,
            AnalyticsProperties analyticsProperties
    ) {
        this.engineService = engineService;
        this.catalogService = catalogService;
        this.scheduleRegistry = scheduleRegistry;
        this.leaderLockService = leaderLockService;
        this.clusterProperties = clusterProperties;
        this.analyticsProperties = analyticsProperties;
        taskScheduler.setPoolSize(1);
        taskScheduler.setThreadNamePrefix("analytics-engine-");
        taskScheduler.initialize();
    }

    @EventListener(ApplicationReadyEvent.class)
    public void onReady() {
        if (!engineService.isEnabled()) {
            return;
        }
        scheduleRegistry.syncAll(catalogService.listEnabledTags());
        reschedule();
    }

    public synchronized void reschedule() {
        cancelWake();
        if (!engineService.isEnabled() || scheduleRegistry.countEnabled() == 0) {
            return;
        }
        Instant nextWakeAt = scheduleRegistry.nextWakeAt();
        if (nextWakeAt == null) {
            return;
        }
        long delayMs = Math.max(1L, Duration.between(Instant.now(), nextWakeAt).toMillis());
        wakeTask.set(taskScheduler.schedule(this::runDueAndReschedule, Instant.now().plusMillis(delayMs)));
    }

    public void syncSchedules() {
        scheduleRegistry.syncAll(catalogService.listEnabledTags());
        reschedule();
    }

    /** Test hook and manual trigger. */
    public void tick() {
        runDueAndReschedule();
    }

    @Scheduled(fixedDelay = 15_000)
    public void leaderFailoverProbe() {
        if (!engineService.isEnabled() || scheduleRegistry.countEnabled() == 0 || isWakeScheduled()) {
            return;
        }
        reschedule();
    }

    private void runDueAndReschedule() {
        if (!engineService.isEnabled() || !clusterProperties.isSchedulerActive()) {
            reschedule();
            return;
        }
        if (!leaderLockService.tryAcquire(LOCK_NAME, LOCK_TTL)) {
            reschedule();
            return;
        }
        try {
            Instant now = Instant.now();
            List<String> duePaths = scheduleRegistry.dueTagPaths(now);
            if (!duePaths.isEmpty()) {
                List<AnalyticsTagDefinition> dueTags = catalogService.listEnabledTags().stream()
                        .filter(tag -> duePaths.contains(tag.tagPath()))
                        .toList();
                engineService.evaluateTags(dueTags);
                for (AnalyticsTagDefinition tag : dueTags) {
                    scheduleRegistry.markRan(tag.tagPath(), tag.periodicMs(), now, null);
                }
            }
        } finally {
            leaderLockService.release(LOCK_NAME);
            reschedule();
        }
    }

    private boolean isWakeScheduled() {
        ScheduledFuture<?> future = wakeTask.get();
        return future != null && !future.isDone();
    }

    private void cancelWake() {
        ScheduledFuture<?> future = wakeTask.getAndSet(null);
        if (future != null) {
            future.cancel(false);
        }
    }
}
