package com.ispf.server.ai.generation;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LiveGeneratorEvidenceTest {

    @AfterEach
    void clear() {
        LiveGeneratorEvidence.clearForTest();
    }

    @Test
    void recordsSoftBudgetMetAndMiss(@TempDir Path temp) throws Exception {
        Path out = temp.resolve("live-generator-results.json");
        System.setProperty(LiveGeneratorEvidence.RESULTS_PATH_PROPERTY, out.toString());

        LiveGeneratorEvidence.record(
                "hvac",
                Duration.ofMinutes(3),
                true,
                "app-hvac",
                "root.platform.apps.hvac",
                "root.platform.dashboards.hvac",
                "root.platform.automation.alertRules.hvac"
        );
        String json = Files.readString(out);
        assertTrue(json.contains("\"domain\": \"hvac\""));
        assertTrue(json.contains("\"elapsedMs\": 180000"));
        assertTrue(json.contains("\"softBudgetMet\": true"));
        assertTrue(json.contains("\"functionalOk\": true"));

        LiveGeneratorEvidence.record(
                "mes",
                Duration.ofMinutes(20),
                true,
                "app-mes",
                "root.platform.apps.mes",
                "root.platform.dashboards.mes",
                "root.platform.automation.alertRules.mes"
        );
        json = Files.readString(out);
        assertTrue(json.contains("\"domain\": \"mes\""));
        assertTrue(json.contains("\"elapsedMs\": 1200000"));
        // Aggregate softBudgetMet is false when any domain misses.
        assertTrue(json.contains("\"softBudgetMet\": false"));
        assertFalse(json.contains("\"domains\": [\n                  ]"));
    }
}
