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
}
