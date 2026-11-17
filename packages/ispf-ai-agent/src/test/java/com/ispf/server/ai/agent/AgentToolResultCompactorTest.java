package com.ispf.server.ai.agent;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AgentToolResultCompactorTest {

    @Test
    void truncatesLargeReportRowsForLlm() {
        List<Map<String, Object>> rows = new java.util.ArrayList<>();
        for (int i = 0; i < 50; i++) {
            rows.add(Map.of("value", i));
        }
        Map<String, Object> toolResult = Map.of(
                "status", "OK",
                "path", "root.platform.reports.shift",
                "result", Map.of(
                        "columns", List.of(Map.of("field", "value", "label", "Value")),
                        "rows", rows,
                        "rowCount", 50
                )
        );
        Map<String, Object> compact = AgentToolResultCompactor.compactForLlm("run_report", toolResult);
        @SuppressWarnings("unchecked")
        Map<String, Object> inner = (Map<String, Object>) compact.get("result");
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> compactRows = (List<Map<String, Object>>) inner.get("rows");
        assertEquals(12, compactRows.size());
        assertEquals(50, inner.get("rowCount"));
        assertTrue(Boolean.TRUE.equals(inner.get("truncatedForLlm")));
    }

    @Test
    void compactsExampleBundleManifestForLlm() {
        Map<String, Object> toolResult = new java.util.LinkedHashMap<>(Map.of(
                "status", "OK",
                "appId", "mes-reference",
                "sectionsRequested", false,
                "manifest", Map.of(
                        "migrations", List.of(Map.of("id", "001")),
                        "functions", Map.of("fn1", Map.of()),
                        "objects", List.of()
                )
        ));
        Map<String, Object> compact = AgentToolResultCompactor.compactForLlm("get_example_bundle", toolResult);
        assertTrue(Boolean.TRUE.equals(compact.get("truncatedForLlm")));
        assertTrue(compact.containsKey("llmHint"));
        assertTrue(!compact.containsKey("manifest"));
        @SuppressWarnings("unchecked")
        Map<String, Object> sections = (Map<String, Object>) compact.get("manifestSections");
        assertEquals(1, ((Map<?, ?>) sections.get("migrations")).get("itemCount"));
        assertEquals(1, ((Map<?, ?>) sections.get("functions")).get("keyCount"));
        assertEquals(0, ((Map<?, ?>) sections.get("objects")).get("itemCount"));
    }

    @Test
    void keepsExampleBundleManifestWhenSectionsRequested() {
        Map<String, Object> toolResult = new java.util.LinkedHashMap<>();
        toolResult.put("status", "OK");
        toolResult.put("appId", "mes-reference");
        toolResult.put("sectionsRequested", true);
        toolResult.put("requestedSections", List.of("functions"));
        toolResult.put("manifest", Map.of("functions", Map.of("fn1", Map.of("type", "script"))));
        Map<String, Object> compact = AgentToolResultCompactor.compactForLlm("get_example_bundle", toolResult);
        assertTrue(compact.containsKey("manifest"));
        assertTrue(!Boolean.TRUE.equals(compact.get("truncatedForLlm")));
    }

    @Test
    void compactsLargeDriverListAndKeepsCount() {
        List<Map<String, Object>> rows = new java.util.ArrayList<>();
        for (int i = 0; i < 160; i++) {
            rows.add(Map.of(
                    "driverId", "d" + i,
                    "name", "Driver " + i,
                    "maturity", "PRODUCTION",
                    "evidence", "LOOPBACK_VERIFIED",
                    "readOnly", false,
                    "capabilities", List.of("read"),
                    "description", "Long description for driver number " + i + " with extra words"
            ));
        }
        Map<String, Object> toolResult = new java.util.LinkedHashMap<>();
        toolResult.put("status", "OK");
        toolResult.put("count", 160);
        toolResult.put("drivers", rows);
        Map<String, Object> compact = AgentToolResultCompactor.compactForLlm("list_drivers", toolResult);
        assertEquals(160, compact.get("count"));
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> drivers = (List<Map<String, Object>>) compact.get("drivers");
        assertEquals(120, drivers.size());
        assertTrue(Boolean.TRUE.equals(compact.get("truncatedForLlm")));
        assertTrue(drivers.get(0).containsKey("driverId"));
        assertTrue(!drivers.get(0).containsKey("capabilities"));
        String json = drivers.toString();
        assertTrue(json.length() < 14_000);
    }
}
