package com.ispf.server.ai.agent;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class AgentToolArgCoercionTest {

    @Test
    void mapsAlternatePackKeysForEnableAgentToolPack() {
        Map<String, Object> coerced = AgentToolArgCoercion.coerce(
                "enable_agent_tool_pack",
                Map.of("name", "devices")
        );
        assertThat(coerced.get("pack")).isEqualTo("devices");
    }

    @Test
    void keepsExplicitPack() {
        Map<String, Object> coerced = AgentToolArgCoercion.coerce(
                "enable_agent_tool_pack",
                Map.of("pack", "Dashboards")
        );
        assertThat(coerced.get("pack")).isEqualTo("dashboards");
    }

    @Test
    void leavesEmptyPackUntouchedWhenNoAlias() {
        Map<String, Object> coerced = AgentToolArgCoercion.coerce(
                "enable_agent_tool_pack",
                Map.of()
        );
        assertThat(coerced.containsKey("pack")).isFalse();
    }

    @Test
    void mapsReportIdToPathForConfigureReport() {
        Map<String, Object> coerced = AgentToolArgCoercion.coerce(
                "configure_report",
                Map.of("reportId", "report-table-all", "reportType", "tree-variables")
        );
        assertThat(coerced.get("path")).isEqualTo("root.platform.reports.report-table-all");
        assertThat(coerced.get("reportId")).isEqualTo("report-table-all");
    }

    @Test
    void mapsAlertNameToPathForConfigureAlert() {
        Map<String, Object> coerced = AgentToolArgCoercion.coerce(
                "configure_alert",
                Map.of("name", "alert-event1-event2-virt-lab")
        );
        assertThat(coerced.get("path")).isEqualTo("root.platform.alert-rules.alert-event1-event2-virt-lab");
        assertThat(coerced.get("name")).isEqualTo("alert-event1-event2-virt-lab");
    }

    @Test
    void searchContextDefaultsQueryFromTopic() {
        Map<String, Object> coerced = AgentToolArgCoercion.coerce(
                "search_context",
                Map.of("topic", "agent-knowledge")
        );
        assertThat(coerced.get("query")).isEqualTo("agent-knowledge");
        assertThat(coerced.get("topic")).isEqualTo("agent-knowledge");
    }

    @Test
    void searchContextMapsQAliasToQuery() {
        Map<String, Object> coerced = AgentToolArgCoercion.coerce(
                "search_context",
                Map.of("q", "bindings", "topic", "all")
        );
        assertThat(coerced.get("query")).isEqualTo("bindings");
    }

    @Test
    void searchContextDoesNotDefaultQueryFromTopicAll() {
        Map<String, Object> coerced = AgentToolArgCoercion.coerce(
                "search_context",
                Map.of("topic", "all")
        );
        assertThat(coerced.containsKey("query")).isFalse();
    }
}
