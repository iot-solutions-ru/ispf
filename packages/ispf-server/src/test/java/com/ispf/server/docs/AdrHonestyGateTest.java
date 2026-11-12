package com.ispf.server.docs;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * Keeps ADR honesty tables (Done / Partial / Gap) aligned with the code they describe.
 * A claim may move to Done only in the same change that lands the implementation.
 */
class AdrHonestyGateTest {

    @Test
    void historianCatchUpClaimMatchesCode() throws IOException {
        boolean implemented = Files.exists(repoRoot().resolve(
                "packages/ispf-server/src/main/java/com/ispf/server/history/HistorianRangeCatchUpService.java"));
        String adr = Files.readString(repoRoot().resolve("docs/en/decisions/0035-historian-dual-write.md"));
        String row = rowContaining(adr, "catch-up");
        if (implemented) {
            assertTrue(row.contains("**Done**"),
                    "HistorianRangeCatchUpService exists; ADR-0035 catch-up must be Done");
            assertFalse(row.contains("**Gap**"),
                    "ADR-0035 still marks catch-up as Gap after the service landed");
        } else {
            assertTrue(row.contains("**Gap**"),
                    "ADR-0035 must keep catch-up as Gap until HistorianRangeCatchUpService exists");
        }
    }

    @Test
    void invokeAgentToolLoopStaysGapUntilImplemented() throws IOException {
        Path service = repoRoot().resolve(
                "packages/ispf-server-workflow/src/main/java/com/ispf/server/workflow/WorkflowAiActionService.java");
        String source = Files.readString(service);
        boolean promptOnly = source.contains("multi-step tool loop is not implemented");
        String adr = Files.readString(repoRoot().resolve("docs/en/decisions/0049-ot-automation-excellence.md"));
        String row = rowContaining(adr, "INVOKE_AGENT");
        if (promptOnly) {
            assertTrue(row.contains("**Gap**") || row.contains("**Partial**"),
                    "prompt-only INVOKE_AGENT must not be documented as a Done tool loop");
            assertFalse(row.contains("bounded multi-step tool loop **Done**"),
                    "tool loop is still rejected in WorkflowAiActionService");
        }
    }

    @Test
    void plainTcpTlsDriversAreNotProduction() throws IOException {
        Path matrix = repoRoot().resolve(
                "packages/ispf-server-driver/src/main/java/com/ispf/server/driver/DriverProductionMatrix.java");
        if (!Files.exists(matrix)) {
            matrix = repoRoot().resolve(
                    "packages/ispf-server/src/main/java/com/ispf/server/driver/DriverProductionMatrix.java");
        }
        String matrixSource = Files.readString(matrix);
        List<String> problems = new ArrayList<>();
        record Driver(String id, String relativeSource) {}
        for (Driver driver : List.of(
                new Driver("aws-iot-core",
                        "packages/ispf-driver-aws-iot-core/src/main/java/com/ispf/driver/awsiotcore/AwsIotCoreDeviceDriver.java"),
                new Driver("azure-iot-hub",
                        "packages/ispf-driver-azure-iot-hub/src/main/java/com/ispf/driver/azureiothub/AzureIotHubDeviceDriver.java")
        )) {
            Path source = repoRoot().resolve(driver.relativeSource());
            if (!Files.exists(source)) {
                continue;
            }
            String body = Files.readString(source);
            boolean plainTcpWhileTlsRequested = body.contains("connecting with MQTT codec on plain TCP");
            if (plainTcpWhileTlsRequested && matrixEntryIsProduction(matrixSource, driver.id())) {
                problems.add(driver.id() + " still connects in cleartext when useTls=true but matrix says PRODUCTION");
            }
        }
        String promotion = Files.readString(repoRoot().resolve("docs/en/driver-promotion.md"));
        for (String driverId : List.of("aws-iot-core", "azure-iot-hub")) {
            if (!matrixEntryIsProduction(matrixSource, driverId)) {
                String row = rowContaining(promotion, "`" + driverId + "`");
                if (row.contains("**PRODUCTION**")) {
                    problems.add(driverId + " is BETA in DriverProductionMatrix but PRODUCTION in driver-promotion.md");
                }
            }
        }
        if (!problems.isEmpty()) {
            fail(String.join("\n", problems));
        }
    }

    private static boolean matrixEntryIsProduction(String matrixSource, String driverId) {
        int idAt = matrixSource.indexOf("\"" + driverId + "\"");
        if (idAt < 0) {
            return false;
        }
        int windowEnd = Math.min(matrixSource.length(), idAt + 220);
        String window = matrixSource.substring(idAt, windowEnd);
        return window.contains("DriverMaturity.PRODUCTION");
    }

    private static String rowContaining(String markdown, String needle) {
        for (String line : markdown.split("\n")) {
            if (line.contains(needle) && line.trim().startsWith("|")) {
                return line;
            }
        }
        fail("no honesty row containing " + needle);
        return "";
    }

    private static Path repoRoot() {
        Path cwd = Path.of("").toAbsolutePath();
        Path[] candidates = {cwd, cwd.getParent(), cwd.resolve("../..").normalize()};
        for (Path candidate : candidates) {
            if (candidate != null && Files.isDirectory(candidate.resolve("docs/en/decisions"))) {
                return candidate;
            }
        }
        return cwd;
    }
}
