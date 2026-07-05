package com.ispf.server.application.binding;

import com.ispf.server.binding.SqlBindingObjectService;
import com.ispf.server.config.ClusterProperties;
import com.ispf.server.platform.PlatformLeaderLockService;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Duration;

@Component
public class ApplicationSqlBindingScheduler {

    private static final String BINDING_LOCK = "application_sql_bindings";

    private final ApplicationSqlBindingService bindingService;
    private final SqlBindingObjectService sqlBindingObjectService;
    private final PlatformLeaderLockService leaderLockService;
    private final ClusterProperties clusterProperties;

    public ApplicationSqlBindingScheduler(
            ApplicationSqlBindingService bindingService,
            SqlBindingObjectService sqlBindingObjectService,
            PlatformLeaderLockService leaderLockService,
            ClusterProperties clusterProperties
    ) {
        this.bindingService = bindingService;
        this.sqlBindingObjectService = sqlBindingObjectService;
        this.leaderLockService = leaderLockService;
        this.clusterProperties = clusterProperties;
    }

    @Scheduled(fixedDelay = 10_000)
    public void refreshScheduledBindings() {
        if (!clusterProperties.isSchedulerActive()) {
            return;
        }
        if (!leaderLockService.tryAcquire(BINDING_LOCK, Duration.ofSeconds(20))) {
            return;
        }
        try {
            bindingService.refreshScheduledBindings();
            sqlBindingObjectService.refreshScheduledBindings();
        } finally {
            leaderLockService.release(BINDING_LOCK);
        }
    }
}
