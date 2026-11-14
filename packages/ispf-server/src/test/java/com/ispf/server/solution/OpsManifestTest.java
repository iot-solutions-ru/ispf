package com.ispf.server.solution;

import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The ops manifest names the backup, bundle-upgrade, and plant-fork tests that
 * already exist, plus the lab gates. Pen-test and field soak stay external.
 */
class OpsManifestTest {

    @Test
    void manifestNamesTheExistingOpsTests() throws Exception {
        Map<?, ?> manifest = new ObjectMapper().readValue(manifestFile().toFile(), Map.class);
        @SuppressWarnings("unchecked")
        List<String> tests = (List<String>) manifest.get("localTests");
        assertEquals(List.of(
                "com.ispf.server.regression.PlatformChangeSetAndBackupRegressionTest",
                "com.ispf.server.application.bundle.BundleUpgradeCompatGateTest",
                "com.ispf.server.solution.PlantDiscreteForkTckTest"
        ), tests);
        for (String name : tests) {
            assertTrue(Files.isRegularFile(sourceFor(name)), name);
        }
        assertEquals(List.of("pen-test", "field-soak"), manifest.get("externalEvidence"));
        assertEquals(List.of("backup-restore", "ispf-node-restart", "nats-restart"), manifest.get("labGates"));
        assertEquals("ispf-db-postgres-1", manifest.get("sharedProductionPostgres"));
        assertTrue(Files.isRegularFile(manifestFile().resolveSibling("ops_cert.py")));
    }

    private static Path sourceFor(String className) {
        String relative = "packages/ispf-server/src/test/java/" + className.replace('.', '/') + ".java";
        Path cwd = Path.of("").toAbsolutePath();
        Path[] candidates = {cwd.resolve(relative), cwd.resolve("../../" + relative).normalize()};
        for (Path candidate : candidates) {
            if (Files.isRegularFile(candidate)) {
                return candidate;
            }
        }
        return candidates[0];
    }

    private static Path manifestFile() {
        Path cwd = Path.of("").toAbsolutePath();
        Path[] candidates = {
                cwd.resolve("examples/solution-tck/ops-manifest.json"),
                cwd.resolve("../../examples/solution-tck/ops-manifest.json").normalize()
        };
        for (Path candidate : candidates) {
            if (Files.isRegularFile(candidate)) {
                return candidate;
            }
        }
        return candidates[0];
    }
}
