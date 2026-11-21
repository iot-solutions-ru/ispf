package com.ispf.server.history;

import com.ispf.server.config.ClusterProperties;
import com.ispf.server.config.MssqlRelationalProperties;
import com.ispf.server.platform.PlatformLeaderLockService;

import java.time.Duration;

/**
 * Leader-elected chart-prep observed_at rollup ticks. Bean-registered (not component-scanned).
 */
public class JdbcMssqlObservedAtRollupMaterializerRunner {

    private static final String LOCK_NAME = "mssql_jdbc_observed_at_rollup";

    private final JdbcMssqlObservedAtRollupMaterializer materializer;
    private final PlatformLeaderLockService leaderLockService;
    private final ClusterProperties clusterProperties;
    private final MssqlRelationalProperties mssqlProperties;

    public JdbcMssqlObservedAtRollupMaterializerRunner(
            JdbcMssqlObservedAtRollupMaterializer materializer,
            PlatformLeaderLockService leaderLockService,
            ClusterProperties clusterProperties,
            MssqlRelationalProperties mssqlProperties
    ) {
        this.materializer = materializer;
        this.leaderLockService = leaderLockService;
        this.clusterProperties = clusterProperties;
        this.mssqlProperties = mssqlProperties;
    }

    @org.springframework.scheduling.annotation.Scheduled(
            fixedDelayString = "${ispf.relational.mssql.observed-rollup-materializer-tick-ms:60000}"
    )
    public void run() {
        if (!materializer.isEnabled()) {
            return;
        }
        if (!clusterProperties.isSchedulerActive()) {
            return;
        }
        long tickMs = Math.max(1_000L, mssqlProperties.getObservedRollupMaterializerTickMs());
        Duration ttl = Duration.ofMillis(Math.max(Duration.ofMinutes(5).toMillis(), tickMs * 2));
        leaderLockService.runIfLeader(LOCK_NAME, ttl, materializer::materializeTick);
    }
}
