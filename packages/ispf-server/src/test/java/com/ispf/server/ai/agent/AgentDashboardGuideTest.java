package com.ispf.server.ai.agent;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AgentDashboardGuideTest {

    @Test
    void referenceTextCoversWorkflowAndAntiPatterns() {
        String text = AgentDashboardGuide.referenceText();
        assertTrue(text.contains("set_dashboard_layout"));
        assertTrue(text.contains("add_dashboard_widget"));
        assertTrue(text.contains("selectionKey"));
        assertTrue(text.contains("list_variables"));
        assertTrue(text.contains("НЕ ДЕЛАТЬ"));
        assertTrue(text.contains("columnsJson"));
    }

    @Test
    void summaryIncludesWorkflowAndTemplates() {
        Map<String, Object> summary = AgentDashboardGuide.summary();
        assertTrue(summary.containsKey("workflow"));
        assertTrue(summary.containsKey("antiPatterns"));
        @SuppressWarnings("unchecked")
        List<String> templates = (List<String>) summary.get("templates");
        assertTrue(templates.contains("snmp-host-monitoring"));
    }
}
