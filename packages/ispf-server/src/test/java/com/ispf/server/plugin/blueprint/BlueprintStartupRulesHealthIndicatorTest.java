package com.ispf.server.plugin.blueprint;

import org.junit.jupiter.api.Test;
import org.springframework.boot.health.contributor.Health;
import org.springframework.boot.health.contributor.Status;

import static org.assertj.core.api.Assertions.assertThat;

class BlueprintStartupRulesHealthIndicatorTest {

    @Test
    void upWhenNoProblems() {
        BlueprintStartupRulesHealth health = new BlueprintStartupRulesHealth();
        BlueprintStartupRulesHealthIndicator indicator = new BlueprintStartupRulesHealthIndicator(health);
        assertThat(indicator.health().getStatus()).isEqualTo(Status.UP);
    }

    @Test
    void downWhenUnresolvedRulesReported() {
        BlueprintStartupRulesHealth health = new BlueprintStartupRulesHealth();
        health.report("root.obj#limit: saved instance rule still contains ${...}");
        BlueprintStartupRulesHealthIndicator indicator = new BlueprintStartupRulesHealthIndicator(health);
        Health result = indicator.health();
        assertThat(result.getStatus()).isEqualTo(Status.DOWN);
        assertThat(result.getDetails().get("unresolvedRules")).asList().hasSize(1);
    }
}
