package com.ispf.server.object;

import com.ispf.server.config.BootstrapProperties;
import com.ispf.server.platform.ClusterPlatformBootstrapService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/**
 * Finalizes object tree readiness after all fixture bootstraps and before driver auto-start.
 */
@Component
public class PlatformObjectReadinessGate {

    private static final Logger log = LoggerFactory.getLogger(PlatformObjectReadinessGate.class);

    /** Runs before post-ready startup listeners (driver auto-start, index rebuilds). */
    public static final int OBJECT_TREE_READY_ORDER = Ordered.LOWEST_PRECEDENCE - 1;
    /** One step after {@link #OBJECT_TREE_READY_ORDER}; safe alternative to {@code Ordered.LOWEST_PRECEDENCE + 1} (int overflow). */
    public static final int AFTER_OBJECT_TREE_READY_ORDER = OBJECT_TREE_READY_ORDER + 1;

    private final ObjectManager objectManager;
    private final ClusterPlatformBootstrapService clusterBootstrapService;
    private final BootstrapProperties bootstrapProperties;

    public PlatformObjectReadinessGate(
            ObjectManager objectManager,
            ClusterPlatformBootstrapService clusterBootstrapService,
            BootstrapProperties bootstrapProperties
    ) {
        this.objectManager = objectManager;
        this.clusterBootstrapService = clusterBootstrapService;
        this.bootstrapProperties = bootstrapProperties;
    }

    @EventListener(ApplicationReadyEvent.class)
    @Order(Ordered.LOWEST_PRECEDENCE - 6)
    public void syncFollowerTreeBeforeDrivers() {
        if (!clusterBootstrapService.isClusterActive()) {
            return;
        }
        if (clusterBootstrapService.shouldRunFixtureBootstrap()) {
            return;
        }
        clusterBootstrapService.waitForFixtureBootstrapComplete();
        log.info("Reloading object tree from shared database (cluster follower)");
        objectManager.reloadFromDatabase();
    }

    @EventListener(ApplicationReadyEvent.class)
    @Order(OBJECT_TREE_READY_ORDER)
    public void markObjectTreeReady() {
        if (clusterBootstrapService.isClusterActive() && clusterBootstrapService.isFixtureBootstrapLeader()) {
            clusterBootstrapService.releaseFixtureBootstrapLock();
        }
        objectManager.markInitialized();
        log.info("Object tree ready for API reads");
    }
}
