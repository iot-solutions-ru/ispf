package com.ispf.server.platform;

import com.ispf.server.config.BootstrapProperties;
import com.ispf.server.config.ClusterProperties;
import com.ispf.server.persistence.ObjectNodeRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Coordinates demo fixture bootstrap across cluster replicas: one leader seeds fixtures,
 * followers wait for a stable DB snapshot then reload the in-memory object tree.
 */
@Service
public class ClusterPlatformBootstrapService {

    private static final Logger log = LoggerFactory.getLogger(ClusterPlatformBootstrapService.class);
    static final String FIXTURE_BOOTSTRAP_LOCK = "platform_fixture_bootstrap";
    private static final Duration FIXTURE_LOCK_TTL = Duration.ofMinutes(45);
    private static final String DEVICE_PATH_PREFIX = "root.platform.devices.";

    private final ClusterProperties clusterProperties;
    private final BootstrapProperties bootstrapProperties;
    private final PlatformLeaderLockService leaderLockService;
    private final ObjectNodeRepository nodeRepository;
    private final AtomicBoolean fixtureLeaderResolved = new AtomicBoolean();
    private volatile boolean fixtureBootstrapLeader;

    public ClusterPlatformBootstrapService(
            ClusterProperties clusterProperties,
            BootstrapProperties bootstrapProperties,
            PlatformLeaderLockService leaderLockService,
            ObjectNodeRepository nodeRepository
    ) {
        this.clusterProperties = clusterProperties;
        this.bootstrapProperties = bootstrapProperties;
        this.leaderLockService = leaderLockService;
        this.nodeRepository = nodeRepository;
    }

    /**
     * Resolves fixture bootstrap role once per JVM (call before {@link #shouldRunFixtureBootstrap()}).
     */
    public void prepareFixtureBootstrapRole() {
        if (!fixtureLeaderResolved.compareAndSet(false, true)) {
            return;
        }
        if (!clusterProperties.enabled() || !bootstrapProperties.isFixturesEnabled()) {
            fixtureBootstrapLeader = bootstrapProperties.isFixturesEnabled();
            return;
        }
        fixtureBootstrapLeader = leaderLockService.tryAcquire(FIXTURE_BOOTSTRAP_LOCK, FIXTURE_LOCK_TTL);
        log.info(
                "Cluster fixture bootstrap role: {} (replica fixtures={})",
                fixtureBootstrapLeader ? "leader" : "follower",
                bootstrapProperties.isFixturesEnabled()
        );
    }

    public boolean isClusterActive() {
        return clusterProperties.enabled();
    }

    public boolean shouldRunFixtureBootstrap() {
        if (!bootstrapProperties.isFixturesEnabled()) {
            return false;
        }
        if (!clusterProperties.enabled()) {
            return true;
        }
        if (!fixtureLeaderResolved.get()) {
            prepareFixtureBootstrapRole();
        }
        return fixtureBootstrapLeader;
    }

    public boolean isFixtureBootstrapLeader() {
        if (!fixtureLeaderResolved.get()) {
            prepareFixtureBootstrapRole();
        }
        return fixtureBootstrapLeader;
    }

    public void releaseFixtureBootstrapLock() {
        if (clusterProperties.enabled() && fixtureBootstrapLeader) {
            leaderLockService.release(FIXTURE_BOOTSTRAP_LOCK);
        }
    }

    /**
     * Followers (and replicas with fixtures disabled) wait until device fixtures in DB stop changing.
     */
    public void waitForFixtureBootstrapComplete() {
        if (!clusterProperties.enabled()) {
            return;
        }
        if (shouldRunFixtureBootstrap()) {
            return;
        }
        log.info("Waiting for cluster fixture bootstrap to complete in shared database...");
        long lastCount = -1;
        int stablePolls = 0;
        for (int attempt = 0; attempt < 120; attempt++) {
            long deviceNodes = countDeviceNodesInDatabase();
            if (deviceNodes > 0 && deviceNodes == lastCount) {
                stablePolls++;
                if (stablePolls >= 3) {
                    log.info("Cluster fixture bootstrap complete ({} device nodes in DB)", deviceNodes);
                    return;
                }
            } else {
                stablePolls = 0;
            }
            lastCount = deviceNodes;
            sleepQuietly(Duration.ofSeconds(2));
        }
        log.warn("Timed out waiting for cluster fixture bootstrap; proceeding with {} device nodes in DB", lastCount);
    }

    long countDeviceNodesInDatabase() {
        return nodeRepository.findAllByOrderByPathAsc().stream()
                .filter(entity -> entity.getPath().startsWith(DEVICE_PATH_PREFIX))
                .count();
    }

    private static void sleepQuietly(Duration duration) {
        try {
            Thread.sleep(duration.toMillis());
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
        }
    }
}
