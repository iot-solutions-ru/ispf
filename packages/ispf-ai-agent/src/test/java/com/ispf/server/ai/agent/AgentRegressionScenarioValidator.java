package com.ispf.server.ai.agent;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ObjectNode;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * BL-177 / BL-178: schema validation for agent-regression scenario JSON (no LLM).
 */
final class AgentRegressionScenarioValidator {

    static final int MIN_SCENARIO_COUNT = 50;

    private static final Set<String> DOMAINS = Set.of("scada", "mes", "hvac");
    private static final Set<String> SCENARIO_PROPERTIES = Set.of(
            "id", "version", "domain", "kind", "title", "description", "prompt", "playbook", "bundle", "acceptance"
    );
    private static final Set<String> KINDS = Set.of("platform-primitive");
    private static final Set<String> BUNDLE_PROPERTIES = Set.of("appId", "manifestPath");
    private static final Set<String> ACCEPTANCE_PROPERTIES = Set.of(
            "validateBundle", "requiredTools", "requiredObjectPaths"
    );
    private static final Pattern ID_PATTERN = Pattern.compile("^[a-z0-9][a-z0-9-]*$");
    private static final Pattern APP_ID_PATTERN = Pattern.compile("^[a-z][a-z0-9-]*$");
    private static final List<String> BUNDLE_REQUIRED = List.of("version", "displayName");

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final Path repoRoot;

    AgentRegressionScenarioValidator(Path repoRoot) {
        this.repoRoot = repoRoot;
    }

    static Path resolveRepoRoot() {
        Path repoRoot = Path.of(System.getProperty("user.dir"));
        for (int depth = 0; depth <= 4
                && !repoRoot.resolve("tools/agent-regression/scenarios").toFile().isDirectory(); depth++) {
            Path parent = repoRoot.getParent();
            if (parent == null) {
                break;
            }
            repoRoot = parent;
        }
        return repoRoot;
    }

    ValidationReport validateAll() throws IOException {
        Path scenariosDir = repoRoot.resolve("tools/agent-regression/scenarios");
        if (!Files.isDirectory(scenariosDir)) {
            throw new IOException("Scenarios directory not found: " + scenariosDir);
        }

        List<String> files = Files.list(scenariosDir)
                .filter(path -> path.getFileName().toString().endsWith(".json"))
                .map(path -> path.getFileName().toString())
                .sorted()
                .toList();

        ValidationReport report = new ValidationReport();
        report.total = files.size();

        for (String file : files) {
            ScenarioResult result = validateScenarioFile(scenariosDir.resolve(file), file);
            report.results.add(result);
            report.byDomain.merge(result.domain, 1, Integer::sum);
            if (result.errors.isEmpty()) {
                report.passed++;
            } else {
                report.failed++;
            }
            if (result.bundleChecked) {
                report.bundleChecks++;
            }
        }
        return report;
    }

    private ScenarioResult validateScenarioFile(Path filePath, String fileName) throws IOException {
        ScenarioResult result = new ScenarioResult(fileName);
        JsonNode scenario = objectMapper.readTree(Files.readString(filePath));

        if (!(scenario instanceof ObjectNode scenarioObject)) {
            result.errors.add("root must be an object");
            return result;
        }

        scenarioObject.propertyNames().forEach(field -> {
            if (!SCENARIO_PROPERTIES.contains(field)) {
                result.errors.add("unexpected property: " + field);
            }
        });

        for (String key : List.of("id", "version", "domain", "title", "prompt")) {
            if (!scenario.hasNonNull(key) || scenario.get(key).asText("").isBlank()) {
                result.errors.add("missing " + key);
            }
        }

        String domain = scenario.path("domain").asText("");
        result.domain = domain;
        if (!domain.isBlank() && !DOMAINS.contains(domain)) {
            result.errors.add("invalid domain: " + domain);
        }

        String kind = scenario.path("kind").asText("");
        if (!kind.isBlank() && !KINDS.contains(kind)) {
            result.errors.add("invalid kind: " + kind);
        }

        String id = scenario.path("id").asText("");
        if (!id.isBlank() && !ID_PATTERN.matcher(id).matches()) {
            result.errors.add("invalid id pattern: " + id);
        }

        if (scenario.path("title").asText("").length() < 3) {
            result.errors.add("title too short");
        }
        if (scenario.path("prompt").asText("").length() < 10) {
            result.errors.add("prompt too short");
        }

        JsonNode bundle = scenario.get("bundle");
        if (bundle != null && !bundle.isNull()) {
            validateBundleNode(bundle, result);
        }

        JsonNode acceptance = scenario.get("acceptance");
        if (acceptance instanceof ObjectNode acceptanceObject) {
            acceptanceObject.propertyNames().forEach(field -> {
                if (!ACCEPTANCE_PROPERTIES.contains(field)) {
                    result.errors.add("acceptance unexpected property: " + field);
                }
            });
        }

        return result;
    }

    private void validateBundleNode(JsonNode bundle, ScenarioResult result) throws IOException {
        if (bundle instanceof ObjectNode bundleObject) {
            bundleObject.propertyNames().forEach(field -> {
                if (!BUNDLE_PROPERTIES.contains(field)) {
                    result.errors.add("bundle unexpected property: " + field);
                }
            });
        }

        String appId = bundle.path("appId").asText("");
        String manifestPath = bundle.path("manifestPath").asText("");
        if (appId.isBlank() || manifestPath.isBlank()) {
            result.errors.add("bundle requires appId and manifestPath");
            return;
        }
        if (!APP_ID_PATTERN.matcher(appId).matches()) {
            result.errors.add("invalid bundle appId: " + appId);
        }

        Path manifestFile = repoRoot.resolve(manifestPath);
        if (!Files.isRegularFile(manifestFile)) {
            result.errors.add("bundle not found: " + manifestPath);
            return;
        }

        JsonNode manifest = objectMapper.readTree(Files.readString(manifestFile));
        for (String key : BUNDLE_REQUIRED) {
            if (!manifest.hasNonNull(key) || manifest.get(key).asText("").isBlank()) {
                result.errors.add(manifestPath + ": missing " + key);
            }
        }
        String operatorAppId = manifest.path("operatorUi").path("appId").asText("");
        if (!operatorAppId.isBlank() && !operatorAppId.equals(appId)) {
            result.errors.add(manifestPath + ": operatorUi.appId mismatch (expected " + appId + ")");
        }
        result.bundleChecked = true;
    }

    static final class ValidationReport {
        int total;
        int passed;
        int failed;
        int bundleChecks;
        final Map<String, Integer> byDomain = new LinkedHashMap<>();
        final List<ScenarioResult> results = new ArrayList<>();
    }

    static final class ScenarioResult {
        final String file;
        String domain = "unknown";
        boolean bundleChecked;
        final List<String> errors = new ArrayList<>();

        ScenarioResult(String file) {
            this.file = file;
        }
    }
}
