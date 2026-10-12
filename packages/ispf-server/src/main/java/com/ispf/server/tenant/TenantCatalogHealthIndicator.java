package com.ispf.server.tenant;

import org.springframework.boot.health.contributor.Health;
import org.springframework.boot.health.contributor.HealthIndicator;
import org.springframework.stereotype.Component;

/**
 * Reports tenant platform-catalog backfill. Contributor name is {@code tenantCatalog}
 * and is part of the readiness health group.
 */
@Component
public class TenantCatalogHealthIndicator implements HealthIndicator {

    private final TenantPlatformCatalogBootstrap bootstrap;

    public TenantCatalogHealthIndicator(TenantPlatformCatalogBootstrap bootstrap) {
        this.bootstrap = bootstrap;
    }

    @Override
    public Health health() {
        if (bootstrap.isContourReady()) {
            return Health.up().build();
        }
        return Health.down().withDetail("error", bootstrap.backfillFailure()).build();
    }
}
