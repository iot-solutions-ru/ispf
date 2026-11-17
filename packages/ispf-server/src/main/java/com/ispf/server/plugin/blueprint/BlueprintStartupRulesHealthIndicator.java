package com.ispf.server.plugin.blueprint;

import org.springframework.boot.health.contributor.Health;
import org.springframework.boot.health.contributor.HealthIndicator;
import org.springframework.stereotype.Component;

/**
 * Reports leftover unresolved placeholders (or missing saved rules) after startup sync.
 * Contributor name is {@code blueprintStartupRules} and is part of the readiness group.
 */
@Component
public class BlueprintStartupRulesHealthIndicator implements HealthIndicator {

    private final BlueprintStartupRulesHealth health;

    public BlueprintStartupRulesHealthIndicator(BlueprintStartupRulesHealth health) {
        this.health = health;
    }

    @Override
    public Health health() {
        if (health.isReady()) {
            return Health.up().build();
        }
        return Health.down().withDetail("unresolvedRules", health.problems()).build();
    }
}
