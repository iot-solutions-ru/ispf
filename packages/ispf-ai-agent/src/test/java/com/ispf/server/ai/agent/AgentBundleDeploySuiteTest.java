package com.ispf.server.ai.agent;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

/**
 * BL-178 platform path: deterministic deploy of every scenario that references a bundle
 * (no LLM). Groups by manifest path so shared fixtures deploy once. Writes
 * {@code build/agent-regression/bundle-suite-results.json} for --enforce-rate --oneshot.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AgentBundleDeploySuiteTest {

    private static final double TARGET_PASS_RATE = 0.95;

    @Autowired
    private PlatformAgentToolRegistry toolRegistry;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private MockMvc mockMvc;

    @Test
    @Timeout(value = 45, unit = TimeUnit.MINUTES)
    void allBundleScenariosDeployViaPlaybookTools() throws Exception {
        Path repoRoot = AgentRegressionScenarioValidator.resolveRepoRoot();
        Path scenariosDir = repoRoot.resolve("tools/agent-regression/scenarios");
        Path out = resolveResultsPath(repoRoot);

        List<BundleScenario> scenarios = loadBundleScenarios(scenariosDir);
        assertTrue(scenarios.size() >= 15, "expected many bundle scenarios, found " + scenarios.size());

        Map<String, List<BundleScenario>> byManifest = new LinkedHashMap<>();
        for (BundleScenario scenario : scenarios) {
            byManifest.computeIfAbsent(scenario.manifestPath(), key -> new ArrayList<>()).add(scenario);
        }

        Map<String, String> statusByScenarioId = new LinkedHashMap<>();
        Map<String, String> errorByScenarioId = new LinkedHashMap<>();

        for (Map.Entry<String, List<BundleScenario>> entry : byManifest.entrySet()) {
            List<BundleScenario> group = entry.getValue();
            BundleScenario first = group.getFirst();
            DeployOutcome outcome = deployManifest(repoRoot, first);
            for (BundleScenario scenario : group) {
                statusByScenarioId.put(scenario.id(), outcome.status());
                if (outcome.error() != null) {
                    errorByScenarioId.put(scenario.id(), outcome.error());
                }
            }
            System.out.println("BL-178 bundle suite: " + first.manifestPath()
                    + " (" + group.size() + " scenarios) -> " + outcome.status()
                    + (outcome.error() != null ? " (" + outcome.error() + ")" : ""));
        }

        List<Map<String, Object>> results = new ArrayList<>();
        int passed = 0;
        for (BundleScenario scenario : scenarios) {
            String status = statusByScenarioId.getOrDefault(scenario.id(), "ERROR");
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("id", scenario.id());
            row.put("status", status);
            if (errorByScenarioId.containsKey(scenario.id())) {
                row.put("error", errorByScenarioId.get(scenario.id()));
            }
            results.add(row);
            if ("OK".equals(status)) {
                passed++;
            }
        }
        writeResults(out, results);

        double rate = (double) passed / scenarios.size();
        System.out.println("BL-178 bundle suite rate: " + passed + "/" + scenarios.size()
                + " (" + String.format(java.util.Locale.ROOT, "%.1f%%", rate * 100) + ")");
        assertTrue(
                rate + 1e-9 >= TARGET_PASS_RATE,
                () -> "bundle deploy pass rate " + rate + " below " + TARGET_PASS_RATE + " — see " + out
        );
    }

    @SuppressWarnings("unchecked")
    private DeployOutcome deployManifest(Path repoRoot, BundleScenario scenario) {
        try {
            Path manifestPath = repoRoot.resolve(scenario.manifestPath());
            if (!Files.isRegularFile(manifestPath)) {
                return DeployOutcome.error("manifest missing: " + scenario.manifestPath());
            }
            Map<String, Object> manifest = objectMapper.readValue(Files.readString(manifestPath), Map.class);
            String appId = scenario.appId();
            AgentContext context = new AgentContext("admin", null, new AgentRunState());
            Map<String, Object> args = Map.of("appId", appId, "manifest", manifest);

            Map<String, Object> validate = toolRegistry.execute("deploy_step_validate", args, context);
            if (!ok(validate)) {
                return DeployOutcome.error("validate: " + validate.get("error"));
            }
            Map<String, Object> dryRun = toolRegistry.execute("deploy_step_dry_run", args, context);
            if (!ok(dryRun)) {
                return DeployOutcome.error("dry_run: " + dryRun.get("error"));
            }
            Map<String, Object> imported = toolRegistry.execute("deploy_step_import", args, context);
            if (!ok(imported)) {
                return DeployOutcome.error("import: " + imported.get("error"));
            }

            // Platform path Done = validate + dry-run + import. Operator UI is optional when
            // the fixture ships empty dashboards / operatorManifest-only (common in demos).
            Object operatorUiRaw = manifest.get("operatorUi");
            if (operatorUiRaw instanceof Map<?, ?> operatorUiMap
                    && hasUsableDashboards(operatorUiMap.get("dashboards"))) {
                Map<String, Object> uiArgs = new LinkedHashMap<>();
                Object uiAppId = operatorUiMap.get("appId");
                uiArgs.put("appId", uiAppId != null ? uiAppId : appId);
                uiArgs.put("title", operatorUiMap.get("title"));
                uiArgs.put("defaultDashboard", operatorUiMap.get("defaultDashboard"));
                uiArgs.put("dashboards", operatorUiMap.get("dashboards"));
                Map<String, Object> ui = toolRegistry.execute("deploy_step_operator_ui", uiArgs, context);
                if (!ok(ui)) {
                    return DeployOutcome.error("operator_ui: " + ui.get("error"));
                }
                String probeAppId = String.valueOf(uiArgs.get("appId"));
                int http = mockMvc.perform(get("/api/v1/operator-apps/" + probeAppId + "/ui"))
                        .andReturn()
                        .getResponse()
                        .getStatus();
                if (http != 200) {
                    return DeployOutcome.error("operatorUiHttp=" + http);
                }
            }

            return DeployOutcome.ok();
        } catch (Exception ex) {
            return DeployOutcome.error(ex.getMessage() != null ? ex.getMessage() : ex.getClass().getSimpleName());
        }
    }

    private List<BundleScenario> loadBundleScenarios(Path scenariosDir) throws Exception {
        List<BundleScenario> out = new ArrayList<>();
        Set<String> seen = new LinkedHashSet<>();
        try (var stream = Files.list(scenariosDir)) {
            List<Path> files = stream
                    .filter(path -> path.getFileName().toString().endsWith(".json"))
                    .sorted()
                    .toList();
            for (Path file : files) {
                JsonNode node = objectMapper.readTree(Files.readString(file));
                if (!node.path("bundle").hasNonNull("appId") || !node.path("bundle").hasNonNull("manifestPath")) {
                    continue;
                }
                String id = node.path("id").asText("");
                if (id.isBlank() || !seen.add(id)) {
                    continue;
                }
                out.add(new BundleScenario(
                        id,
                        node.path("bundle").path("appId").asText(),
                        node.path("bundle").path("manifestPath").asText()
                ));
            }
        }
        return out;
    }

    private void writeResults(Path out, List<Map<String, Object>> results) throws Exception {
        Files.createDirectories(out.getParent());
        ObjectNode root = objectMapper.createObjectNode();
        root.put("generatedAt", java.time.Instant.now().toString());
        root.put("source", "AgentBundleDeploySuiteTest");
        ArrayNode scenarios = root.putArray("scenarios");
        for (Map<String, Object> row : results) {
            ObjectNode item = scenarios.addObject();
            item.put("id", String.valueOf(row.get("id")));
            item.put("status", String.valueOf(row.get("status")));
            if (row.get("error") != null) {
                item.put("error", String.valueOf(row.get("error")));
            }
        }
        Files.writeString(out, objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(root));
    }

    private static Path resolveResultsPath(Path repoRoot) {
        String configured = System.getenv("AGENT_BUNDLE_SUITE_RESULTS");
        if (configured != null && !configured.isBlank()) {
            Path path = Path.of(configured);
            return path.isAbsolute() ? path : repoRoot.resolve(path);
        }
        return repoRoot.resolve("build/agent-regression/bundle-suite-results.json");
    }

    private static boolean ok(Map<String, Object> step) {
        return "OK".equals(String.valueOf(step.get("status")));
    }

    private static boolean hasUsableDashboards(Object dashboardsRaw) {
        if (!(dashboardsRaw instanceof List<?> list) || list.isEmpty()) {
            return false;
        }
        for (Object item : list) {
            if (item instanceof Map<?, ?> row) {
                Object path = row.get("path");
                if (path != null && !String.valueOf(path).isBlank()) {
                    return true;
                }
            }
        }
        return false;
    }

    private record BundleScenario(String id, String appId, String manifestPath) {
    }

    private record DeployOutcome(String status, String error) {
        static DeployOutcome ok() {
            return new DeployOutcome("OK", null);
        }

        static DeployOutcome error(String error) {
            return new DeployOutcome("ERROR", error);
        }
    }
}
