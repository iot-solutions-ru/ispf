package com.ispf.server.platform;

import org.springframework.boot.health.contributor.Health;
import org.springframework.boot.health.contributor.HealthIndicator;
import org.springframework.stereotype.Component;

/**
 * Reports cluster fixture bootstrap. Contributor name is {@code clusterFixture}
 * and is part of the readiness health group.
 */
@Component
public class ClusterFixtureHealthIndicator implements HealthIndicator {

    private final ClusterPlatformBootstrapService bootstrapService;

    public ClusterFixtureHealthIndicator(ClusterPlatformBootstrapService bootstrapService) {
        this.bootstrapService = bootstrapService;
    }

    @Override
    public Health health() {
        if (bootstrapService.isFixtureBootstrapReady()) {
            return Health.up().build();
        }
        return Health.down().withDetail("error", bootstrapService.fixtureBootstrapFailure()).build();
    }
}
