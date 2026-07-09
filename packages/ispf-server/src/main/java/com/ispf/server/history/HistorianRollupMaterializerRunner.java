package com.ispf.server.history;

import com.ispf.server.config.AnalyticsProperties;
import com.ispf.server.config.ClusterProperties;
import com.ispf.server.platform.PlatformLeaderLockService;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Duration;

/**
 * Leader-elected periodic historian rollup materializer (BL-205).
 */
@Component
public class HistorianRollupMaterializerRunner {

    private static final String LOCK_NAME = "historian_rollup_materializer";
    private static final Duration LOCK_TTL = Duration.ofMinutes(2);

    private final HistorianRollupMaterializerService materializerService;
    private final PlatformLeaderLockService leaderLockService;
    private final ClusterProperties clusterProperties;
    private final AnalyticsProperties analyticsProperties;

    public HistorianRollupMaterializerRunner(
            HistorianRollupMaterializerService materializerService,
            PlatformLeaderLockService leaderLockService,
            ClusterProperties clusterProperties,
            AnalyticsProperties analyticsProperties
    ) {
        this.materializerService = materializerService;
        this.leaderLockService = leaderLockService;
        this.clusterProperties = clusterProperties;
        this.analyticsProperties = analyticsProperties;
    }

    @Scheduled(fixedDelayString = "${ispf.analytics.materializer-tick-ms:60000}")
    public void run() {
        if (!materializerService.isEnabled()) {
            return;
        }
        if (!clusterProperties.isSchedulerActive()) {
            return;
        }
        if (!leaderLockService.tryAcquire(LOCK_NAME, LOCK_TTL)) {
            return;
        }
        try {
            materializerService.materializeTick();
        } finally {
            leaderLockService.release(LOCK_NAME);
        }
    }
}
