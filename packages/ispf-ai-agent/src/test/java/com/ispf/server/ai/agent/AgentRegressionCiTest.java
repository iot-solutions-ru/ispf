package com.ispf.server.ai.agent;

import org.junit.jupiter.api.Test;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * BL-177: CI gate for agent-regression scenario JSON schemas (no LLM).
 */
class AgentRegressionCiTest {

    @Test
    void allScenarioJsonSchemasAreValid() throws Exception {
        Path repoRoot = AgentRegressionScenarioValidator.resolveRepoRoot();
        AgentRegressionScenarioValidator validator = new AgentRegressionScenarioValidator(repoRoot);
        AgentRegressionScenarioValidator.ValidationReport report = validator.validateAll();

        assertTrue(
                report.total >= AgentRegressionScenarioValidator.MIN_SCENARIO_COUNT,
                () -> "expected at least " + AgentRegressionScenarioValidator.MIN_SCENARIO_COUNT
                        + " scenarios, found " + report.total
        );

        for (AgentRegressionScenarioValidator.ScenarioResult result : report.results) {
            assertTrue(
                    result.errors.isEmpty(),
                    () -> result.file + ": " + String.join("; ", result.errors)
            );
        }

        assertEquals(report.total, report.passed);
        assertTrue(report.byDomain.containsKey("scada"));
        assertTrue(report.byDomain.containsKey("mes"));
        assertTrue(report.byDomain.containsKey("hvac"));
        assertTrue(report.bundleChecks >= 15, "expected bundle manifest cross-checks");
    }
}
