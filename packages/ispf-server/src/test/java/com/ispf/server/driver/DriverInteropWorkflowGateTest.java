package com.ispf.server.driver;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * BL-85: PRODUCTION top-10 drivers must map to {@code driver-interop.yml} matrix modules.
 */
class DriverInteropWorkflowGateTest {

    private static final Pattern MODULE_LINE = Pattern.compile("^\\s+-\\s+(ispf-driver-[\\w-]+)\\s*$", Pattern.MULTILINE);

    @Test
    void top10IndustrialDriversDeclareInteropModule() {
        for (String driverId : DriverProductionMatrix.TOP_10_INDUSTRIAL) {
            assertTrue(
                    DriverProductionMatrix.resolveInteropGradleModule(driverId).isPresent(),
                    driverId + " must declare interopGradleModule"
            );
        }
    }

    @Test
    void interopWorkflowMatrixCoversTop10Modules() throws IOException {
        Path workflow = resolveWorkflowPath();
        String yaml = Files.readString(workflow);
        Set<String> workflowModules = parseWorkflowModules(yaml);

        Set<String> expectedModules = new HashSet<>();
        for (String driverId : DriverProductionMatrix.TOP_10_INDUSTRIAL) {
            expectedModules.add(DriverProductionMatrix.resolveInteropGradleModule(driverId).orElseThrow());
        }

        assertEquals(expectedModules, workflowModules, "driver-interop.yml matrix must match top-10 modules exactly");
    }

    @Test
    void productionTop10ModulesAreUniqueInMatrix() {
        Set<String> modules = new HashSet<>();
        for (String driverId : DriverProductionMatrix.TOP_10_INDUSTRIAL) {
            String module = DriverProductionMatrix.resolveInteropGradleModule(driverId).orElseThrow();
            assertTrue(modules.add(module), "duplicate interop module for " + driverId + ": " + module);
        }
        assertEquals(10, modules.size());
    }

    private static Set<String> parseWorkflowModules(String yaml) {
        Set<String> modules = new HashSet<>();
        Matcher matcher = MODULE_LINE.matcher(yaml);
        boolean inMatrix = false;
        for (String line : yaml.split("\n")) {
            if (line.trim().equals("matrix:")) {
                inMatrix = true;
                continue;
            }
            if (inMatrix && line.trim().startsWith("module:")) {
                continue;
            }
            if (inMatrix && !line.isBlank() && !line.startsWith(" ") && !line.startsWith("\t")) {
                break;
            }
        }
        matcher.reset(yaml);
        while (matcher.find()) {
            modules.add(matcher.group(1));
        }
        assertFalse(modules.isEmpty(), "no interop modules parsed from workflow");
        return modules;
    }

    private static Path resolveWorkflowPath() {
        Path fromModule = Path.of("../../.github/workflows/driver-interop.yml").normalize();
        Path fromRoot = Path.of(".github/workflows/driver-interop.yml");
        if (Files.exists(fromModule)) {
            return fromModule;
        }
        if (Files.exists(fromRoot)) {
            return fromRoot;
        }
        throw new IllegalStateException("driver-interop.yml not found");
    }
}
