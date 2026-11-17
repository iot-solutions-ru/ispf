package com.ispf.server.plugin.blueprint;

import org.springframework.boot.health.contributor.Health;
import org.springframework.boot.health.contributor.HealthIndicator;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Reports templated blueprint rules whose saved instance rule was missing or still unresolved at
 * startup and has not been remediated since.
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
        List<String> problems = health.problems();
        if (problems.isEmpty()) {
            return Health.up().build();
        }
        return Health.down().withDetail("unresolvedRules", problems).build();
    }
}
