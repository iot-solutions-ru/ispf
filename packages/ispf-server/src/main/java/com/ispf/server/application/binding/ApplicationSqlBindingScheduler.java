package com.ispf.server.application.binding;

import com.ispf.server.binding.SqlBindingObjectService;
import com.ispf.server.config.ClusterProperties;
import com.ispf.server.object.ObjectManager;
import com.ispf.server.platform.PlatformLeaderLockService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Duration;

@Component
public class ApplicationSqlBindingScheduler {

    private static final Logger log = LoggerFactory.getLogger(ApplicationSqlBindingScheduler.class);

    private static final String BINDING_LOCK = "application_sql_bindings";

    private final ApplicationSqlBindingService bindingService;
    private final SqlBindingObjectService sqlBindingObjectService;
    private final PlatformLeaderLockService leaderLockService;
    private final ClusterProperties clusterProperties;
    private final ObjectManager objectManager;

    public ApplicationSqlBindingScheduler(
            ApplicationSqlBindingService bindingService,
            SqlBindingObjectService sqlBindingObjectService,
            PlatformLeaderLockService leaderLockService,
            ClusterProperties clusterProperties,
            ObjectManager objectManager
    ) {
        this.bindingService = bindingService;
        this.sqlBindingObjectService = sqlBindingObjectService;
        this.leaderLockService = leaderLockService;
        this.clusterProperties = clusterProperties;
        this.objectManager = objectManager;
    }

    @Scheduled(fixedDelay = 10_000)
    public void refreshScheduledBindings() {
        if (!objectManager.isInitialized()) {
            return;
        }
        if (!clusterProperties.isSchedulerActive()) {
            return;
        }
        leaderLockService.runIfLeader(BINDING_LOCK, Duration.ofSeconds(20), () -> {
            refreshSet("application", bindingService::refreshScheduledBindings);
            refreshSet("tree", sqlBindingObjectService::refreshScheduledBindings);
        });
    }

    /** Application and tree bindings are independent: a failure in one set must not skip the other. */
    private static void refreshSet(String kind, Runnable refresh) {
        try {
            refresh.run();
        } catch (RuntimeException ex) {
            log.error("Scheduled refresh of {} SQL bindings failed", kind, ex);
        }
    }
}
