package com.ispf.server.ai.generation;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Durable soft-budget evidence for BL-180 live generator smoke.
 * Written under {@code build/agent-regression/} (or {@code AGENT_LIVE_GENERATOR_RESULTS}).
 * Soft budget miss is recorded honestly; callers decide assume vs hard fail.
 */
final class LiveGeneratorEvidence {

    static final Duration SOFT_DURATION_BUDGET = Duration.ofMinutes(15);

    /** Test override ({@link System#setProperty}); production path uses env / default. */
    static final String RESULTS_PATH_PROPERTY = "ispf.agent.live.generator.results";

    private static final Map<String, DomainRow> ROWS = new ConcurrentHashMap<>();
    private static volatile String runStartedAt = Instant.now().toString();

    private LiveGeneratorEvidence() {
    }

    static Path resultsPath() {
        String prop = System.getProperty(RESULTS_PATH_PROPERTY);
        if (prop != null && !prop.isBlank()) {
            return Path.of(prop);
        }
        String override = System.getenv("AGENT_LIVE_GENERATOR_RESULTS");
        if (override != null && !override.isBlank()) {
            return Path.of(override);
        }
        return Path.of("build", "agent-regression", "live-generator-results.json");
    }

    static synchronized void record(
            String domain,
            Duration elapsed,
            boolean functionalOk,
            String appId,
            String hubPath,
            String dashboardPath,
            String alertPath
    ) throws IOException {
        boolean softMet = elapsed.compareTo(SOFT_DURATION_BUDGET) <= 0;
        ROWS.put(
                domain,
                new DomainRow(
                        domain,
                        functionalOk ? "OK" : "ERROR",
                        elapsed.toMillis(),
                        SOFT_DURATION_BUDGET.toMillis(),
                        softMet,
                        appId,
                        hubPath,
                        dashboardPath,
                        alertPath
                )
        );
        writeAll();
    }

    static synchronized void clearForTest() {
        ROWS.clear();
        runStartedAt = Instant.now().toString();
        System.clearProperty(RESULTS_PATH_PROPERTY);
    }

    static synchronized String renderJson() {
        List<DomainRow> ordered = new ArrayList<>(ROWS.values());
        ordered.sort(Comparator.comparing(DomainRow::domain));
        StringBuilder scenarios = new StringBuilder();
        for (int i = 0; i < ordered.size(); i++) {
            if (i > 0) {
                scenarios.append(",\n");
            }
            scenarios.append(ordered.get(i).toJson(4));
        }
        boolean allSoftMet = !ordered.isEmpty() && ordered.stream().allMatch(DomainRow::softBudgetMet);
        boolean allOk = !ordered.isEmpty() && ordered.stream().allMatch(r -> "OK".equals(r.status()));
        return """
                {
                  "generatedAt": "%s",
                  "runStartedAt": "%s",
                  "source": "AiSolutionGeneratorLiveSmokeTest",
                  "softBudgetMs": %d,
                  "softBudgetMet": %s,
                  "functionalOk": %s,
                  "domains": [
                %s
                  ]
                }
                """.formatted(
                Instant.now().toString(),
                runStartedAt,
                SOFT_DURATION_BUDGET.toMillis(),
                allSoftMet,
                allOk,
                scenarios
        );
    }

    private static void writeAll() throws IOException {
        Path path = resultsPath();
        Files.createDirectories(path.getParent());
        Files.writeString(path, renderJson(), StandardCharsets.UTF_8);
    }

    private record DomainRow(
            String domain,
            String status,
            long elapsedMs,
            long softBudgetMs,
            boolean softBudgetMet,
            String appId,
            String hubPath,
            String dashboardPath,
            String alertPath
    ) {
        String toJson(int indent) {
            String pad = " ".repeat(indent);
            Map<String, String> fields = new LinkedHashMap<>();
            fields.put("domain", quote(domain));
            fields.put("status", quote(status));
            fields.put("elapsedMs", Long.toString(elapsedMs));
            fields.put("softBudgetMs", Long.toString(softBudgetMs));
            fields.put("softBudgetMet", Boolean.toString(softBudgetMet));
            fields.put("appId", quote(nullToEmpty(appId)));
            fields.put("hubPath", quote(nullToEmpty(hubPath)));
            fields.put("dashboardPath", quote(nullToEmpty(dashboardPath)));
            fields.put("alertPath", quote(nullToEmpty(alertPath)));
            StringBuilder sb = new StringBuilder();
            sb.append(pad).append("{\n");
            int i = 0;
            for (Map.Entry<String, String> e : fields.entrySet()) {
                if (i++ > 0) {
                    sb.append(",\n");
                }
                sb.append(pad).append("  \"").append(e.getKey()).append("\": ").append(e.getValue());
            }
            sb.append('\n').append(pad).append('}');
            return sb.toString();
        }

        private static String nullToEmpty(String value) {
            return value == null ? "" : value;
        }

        private static String quote(String value) {
            return "\"" + value.replace("\\", "\\\\").replace("\"", "\\\"") + "\"";
        }
    }
}
