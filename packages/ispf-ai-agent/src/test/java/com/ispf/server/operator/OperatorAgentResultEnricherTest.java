package com.ispf.server.operator;

import com.ispf.server.ai.agent.OperatorAgentScope;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OperatorAgentResultEnricherTest {

    private final OperatorAgentResultEnricher enricher = new OperatorAgentResultEnricher();

    @Test
    void addsUrlToLinksAndExtractsReportTable() {
        OperatorAgentScope scope = new OperatorAgentScope(
                "demo",
                "Demo",
                List.of("root.platform.dashboards.demo", "root.platform.reports.shift"),
                "root.platform.dashboards.demo"
        );
        List<Map<String, Object>> steps = List.of(
                Map.of(
                        "type", "tool",
                        "tool", "run_report",
                        "result", Map.of(
                                "status", "OK",
                                "path", "root.platform.reports.shift",
                                "result", Map.of(
                                        "columns", List.of(Map.of("field", "value", "label", "Value")),
                                        "rows", List.of(Map.of("value", 42)),
                                        "rowCount", 1,
                                        "truncated", false
                                )
                        )
                )
        );
        Map<String, Object> result = enricher.enrich(
                "demo",
                scope,
                steps,
                Map.of("links", List.of(Map.of(
                        "kind", "dashboard",
                        "path", "root.platform.dashboards.demo",
                        "title", "Demo board"
                )))
        );
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> links = (List<Map<String, Object>>) result.get("links");
        assertEquals(2, links.size());
        assertTrue(links.getFirst().get("url").toString().contains("dashboard="));
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> tables = (List<Map<String, Object>>) result.get("tables");
        assertEquals(1, tables.size());
        assertEquals("shift", tables.getFirst().get("title"));
    }

    @Test
    void rejectsOutOfScopeLinks() {
        OperatorAgentScope scope = new OperatorAgentScope(
                "demo",
                "Demo",
                List.of("root.platform.dashboards.demo"),
                "root.platform.dashboards.demo"
        );
        Map<String, Object> result = enricher.enrich(
                "demo",
                scope,
                List.of(),
                Map.of("links", List.of(Map.of(
                        "kind", "report",
                        "path", "root.platform.reports.secret",
                        "title", "Secret"
                )))
        );
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> links = (List<Map<String, Object>>) result.get("links");
        assertTrue(links.isEmpty());
    }

    @Test
    void operatorUrlUsesReportParam() {
        String url = OperatorAgentResultEnricher.operatorUrl("mini-tec", "report", "root.platform.reports.kpi");
        assertTrue(url.contains("mode=operator"));
        assertTrue(url.contains("app=mini-tec"));
        assertTrue(url.contains("report="));
        assertFalse(url.contains("dashboard="));
    }
}
