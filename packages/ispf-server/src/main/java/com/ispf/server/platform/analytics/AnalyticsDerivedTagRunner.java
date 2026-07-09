package com.ispf.server.platform.analytics;

import com.ispf.server.config.AnalyticsProperties;
import com.ispf.server.config.ClusterProperties;
import com.ispf.server.platform.PlatformLeaderLockService;
import com.ispf.server.platform.analytics.engine.AnalyticsEngineService;
import com.ispf.server.platform.analytics.engine.AnalyticsEngineScheduler;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Duration;

/**
 * Legacy periodic tick — delegates to calculation engine (BL-203).
 */
@Component
public class AnalyticsDerivedTagRunner {

    private static final String LOCK_NAME = "analytics_derived_tag_runner";
    private static final Duration LOCK_TTL = Duration.ofSeconds(90);

    private final AnalyticsEngineService engineService;
    private final AnalyticsEngineScheduler engineScheduler;
    private final AnalyticsProperties analyticsProperties;
    private final PlatformLeaderLockService leaderLockService;
    private final ClusterProperties clusterProperties;

    public AnalyticsDerivedTagRunner(
            AnalyticsEngineService engineService,
            AnalyticsEngineScheduler engineScheduler,
            AnalyticsProperties analyticsProperties,
            PlatformLeaderLockService leaderLockService,
            ClusterProperties clusterProperties
    ) {
        this.engineService = engineService;
        this.engineScheduler = engineScheduler;
        this.analyticsProperties = analyticsProperties;
        this.leaderLockService = leaderLockService;
        this.clusterProperties = clusterProperties;
    }

    @Scheduled(fixedDelayString = "${ispf.analytics.derived-tag-tick-ms:60000}")
    public void tick() {
        if (!analyticsProperties.derivedTagEnabled() || !engineService.isEnabled()) {
            return;
        }
        if (!clusterProperties.isSchedulerActive()) {
            return;
        }
        if (!leaderLockService.tryAcquire(LOCK_NAME, LOCK_TTL)) {
            return;
        }
        try {
            engineScheduler.syncSchedules();
            engineService.evaluateAllEnabled();
        } finally {
            leaderLockService.release(LOCK_NAME);
        }
    }
}
