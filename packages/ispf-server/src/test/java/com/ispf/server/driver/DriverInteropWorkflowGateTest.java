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
 * BL-85 / BL-140 / BL-141: PRODUCTION top-20 drivers must map to {@code driver-interop.yml} matrix modules.
 */
class DriverInteropWorkflowGateTest {

    private static final Pattern MODULE_LINE = Pattern.compile("^\\s+-\\s+(ispf-driver-[\\w-]+)\\s*$", Pattern.MULTILINE);

    @Test
    void top20IndustrialDriversDeclareInteropModule() {
        for (String driverId : DriverProductionMatrix.TOP_20_INDUSTRIAL) {
            assertTrue(
                    DriverProductionMatrix.resolveInteropGradleModule(driverId).isPresent(),
                    driverId + " must declare interopGradleModule"
            );
        }
    }

    @Test
    void interopWorkflowMatrixCoversTop20Modules() throws IOException {
        Path workflow = resolveWorkflowPath();
        String yaml = Files.readString(workflow);
        Set<String> workflowModules = parseWorkflowModules(yaml);
        Set<String> expectedModules = interopModulesFor(DriverProductionMatrix.TOP_20_INDUSTRIAL);

        assertEquals(expectedModules, workflowModules, "driver-interop.yml matrix must match top-20 modules exactly");
    }

    @Test
    void productionTop20ModulesAreUniqueInMatrix() {
        Set<String> modules = interopModulesFor(DriverProductionMatrix.TOP_20_INDUSTRIAL);
        assertEquals(DriverProductionMatrix.TOP_20_INDUSTRIAL.size(), modules.size(), "each top-20 driver must map to a unique interop module");
    }

    @Test
    void interopWorkflowMatrixStillCoversTop10Modules() throws IOException {
        Path workflow = resolveWorkflowPath();
        String yaml = Files.readString(workflow);
        Set<String> workflowModules = parseWorkflowModules(yaml);
        Set<String> expectedModules = interopModulesFor(DriverProductionMatrix.TOP_10_INDUSTRIAL);

        assertTrue(workflowModules.containsAll(expectedModules), "top-10 interop modules must remain in workflow");
    }

    private static Set<String> interopModulesFor(java.util.List<String> driverIds) {
        Set<String> modules = new HashSet<>();
        for (String driverId : driverIds) {
            String module = DriverProductionMatrix.resolveInteropGradleModule(driverId).orElseThrow();
            assertTrue(modules.add(module), "duplicate interop module for " + driverId + ": " + module);
        }
        return modules;
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
