package com.ispf.server.object;

import com.ispf.server.config.ClusterProperties;
import com.ispf.server.platform.PlatformLeaderLockService;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Fires periodic binding rules from {@link BindingPeriodicScheduleRegistry} using dynamic one-shot wakes.
 * No tree scan; idle when no periodic rules are registered.
 */
@Component
public class BindingPeriodicScheduler {

    private static final String LOCK_NAME = "binding_periodic_rules";
    private static final Duration LOCK_TTL = Duration.ofSeconds(10);

    private final BindingPeriodicScheduleRegistry registry;
    private final BindingRuleEngine bindingRuleEngine;
    private final PlatformLeaderLockService leaderLockService;
    private final ClusterProperties clusterProperties;
    private final ThreadPoolTaskScheduler taskScheduler = new ThreadPoolTaskScheduler();
    private final AtomicReference<ScheduledFuture<?>> wakeTask = new AtomicReference<>();

    public BindingPeriodicScheduler(
            BindingPeriodicScheduleRegistry registry,
            BindingRuleEngine bindingRuleEngine,
            PlatformLeaderLockService leaderLockService,
            ClusterProperties clusterProperties
    ) {
        this.registry = registry;
        this.bindingRuleEngine = bindingRuleEngine;
        this.leaderLockService = leaderLockService;
        this.clusterProperties = clusterProperties;
        taskScheduler.setPoolSize(1);
        taskScheduler.setThreadNamePrefix("binding-periodic-");
        taskScheduler.initialize();
    }

    @EventListener(ApplicationReadyEvent.class)
    public void onReady() {
        cancelWake();
    }

    public synchronized void reschedule() {
        cancelWake();
        if (registry.countEnabled() == 0) {
            return;
        }
        Instant nextWakeAt = registry.nextWakeAt();
        if (nextWakeAt == null) {
            return;
        }
        long delayMs = Math.max(1L, Duration.between(Instant.now(), nextWakeAt).toMillis());
        ScheduledFuture<?> future = taskScheduler.schedule(
                this::runDueAndReschedule,
                Instant.now().plusMillis(delayMs)
        );
        wakeTask.set(future);
    }

    /** Test hook and manual trigger. */
    public void tick() {
        runDueAndReschedule();
    }

    public boolean isWakeScheduled() {
        ScheduledFuture<?> future = wakeTask.get();
        return future != null && !future.isDone();
    }

    @Scheduled(fixedDelay = 15_000)
    public void leaderFailoverProbe() {
        if (registry.countEnabled() == 0 || isWakeScheduled()) {
            return;
        }
        reschedule();
    }

    private void runDueAndReschedule() {
        if (!clusterProperties.isSchedulerActive()) {
            reschedule();
            return;
        }
        if (!leaderLockService.tryAcquire(LOCK_NAME, LOCK_TTL)) {
            reschedule();
            return;
        }
        try {
            registry.fireDue(Instant.now(), bindingRuleEngine);
        } finally {
            leaderLockService.release(LOCK_NAME);
            reschedule();
        }
    }

    private void cancelWake() {
        ScheduledFuture<?> future = wakeTask.getAndSet(null);
        if (future != null) {
            future.cancel(false);
        }
    }
}
