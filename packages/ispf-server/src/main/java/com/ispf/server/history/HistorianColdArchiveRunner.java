package com.ispf.server.history;

import com.ispf.server.config.ClusterProperties;
import com.ispf.server.platform.PlatformLeaderLockService;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Duration;

/**
 * Leader-elected nightly cold-tier Parquet export (BL-202).
 */
@Component
public class HistorianColdArchiveRunner {

    private static final String LOCK_NAME = "historian_cold_archive";
    private static final Duration LOCK_TTL = Duration.ofHours(2);

    private final HistorianColdArchiveService archiveService;
    private final PlatformLeaderLockService leaderLockService;
    private final ClusterProperties clusterProperties;

    public HistorianColdArchiveRunner(
            HistorianColdArchiveService archiveService,
            PlatformLeaderLockService leaderLockService,
            ClusterProperties clusterProperties
    ) {
        this.archiveService = archiveService;
        this.leaderLockService = leaderLockService;
        this.clusterProperties = clusterProperties;
    }

    @Scheduled(cron = "${ispf.historian.cold-archive.cron:0 0 4 * * *}")
    public void run() {
        if (!archiveService.isEnabled()) {
            return;
        }
        if (!clusterProperties.isSchedulerActive()) {
            return;
        }
        if (!leaderLockService.tryAcquire(LOCK_NAME, LOCK_TTL)) {
            return;
        }
        try {
            archiveService.exportEligibleDay();
        } finally {
            leaderLockService.release(LOCK_NAME);
        }
    }
}
