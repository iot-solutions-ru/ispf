package com.ispf.server.platform.analytics;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * BL-161 hardening: {@code examples/historian-sla-dashboard/} reference layout validates.
 */
class HistorianSlaDashboardExampleTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Test
    void exampleLayoutReferencesHistorianSlaWidgets() throws Exception {
        Path exampleDir = resolveExampleDir();
        JsonNode layout = MAPPER.readTree(Files.readString(exampleDir.resolve("dashboard-layout.json")));
        JsonNode bff = MAPPER.readTree(Files.readString(exampleDir.resolve("bff-functions.example.json")));

        assertEquals(84, layout.path("columns").asInt());
        assertTrue(layout.path("widgets").isArray());
        assertTrue(widgetIds(layout).contains("hist-agg-p95"));
        assertTrue(widgetIds(layout).contains("hist-raw-p95"));
        assertTrue(widgetIds(layout).contains("hist-slo-help"));

        assertTrue(bff.path("dataSource").path("endpoint").asText().contains("/historian-sla"));
        assertTrue(bff.path("functions").isArray());
        assertTrue(bff.path("functions").size() >= 6);
    }

    private static java.util.Set<String> widgetIds(JsonNode layout) {
        java.util.Set<String> ids = new java.util.LinkedHashSet<>();
        for (JsonNode widget : layout.path("widgets")) {
            ids.add(widget.path("id").asText());
        }
        return ids;
    }

    private static Path resolveExampleDir() {
        Path cwd = Path.of(System.getProperty("user.dir")).toAbsolutePath().normalize();
        for (int depth = 0; depth <= 4; depth++) {
            Path candidate = cwd.resolve("examples/historian-sla-dashboard");
            if (Files.isDirectory(candidate)) {
                return candidate;
            }
            cwd = cwd.getParent();
            if (cwd == null) {
                break;
            }
        }
        throw new IllegalStateException("examples/historian-sla-dashboard not found from user.dir");
    }
}
