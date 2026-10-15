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

    @Test
    void ensureSingletonMapsBlueprintNameToId() {
        Map<String, Object> coerced = AgentToolArgCoercion.coerce(
                "ensure_singleton_instance",
                Map.of("blueprintName", "platform-master-v1")
        );
        assertThat(coerced.get("blueprintId")).isEqualTo("platform-master-v1");
    }

    @Test
    void applyMixinMapsPathAndBlueprintName() {
        Map<String, Object> coerced = AgentToolArgCoercion.coerce(
                "apply_mixin_blueprint",
                Map.of("path", "root.platform.devices.dev1", "blueprintName", "virtual-lab-v1")
        );
        assertThat(coerced.get("objectPath")).isEqualTo("root.platform.devices.dev1");
        assertThat(coerced.get("blueprintId")).isEqualTo("virtual-lab-v1");
    }

    @Test
    void createBindingRuleFlattensRuleJsonAndDefaultsTarget() {
        Map<String, Object> coerced = AgentToolArgCoercion.coerce(
                "create_binding_rule",
                Map.of(
                        "objectPath", "root.platform.devices.dev1",
                        "ruleJson", Map.of(
                                "id", "waveSum",
                                "expression", "read(\"self/sineWave\")+read(\"self/sawtoothWave\")"
                        )
                )
        );
        assertThat(coerced.get("path")).isEqualTo("root.platform.devices.dev1");
        assertThat(coerced.get("id")).isEqualTo("waveSum");
        assertThat(coerced.get("expression")).isEqualTo("read(\"self/sineWave\")+read(\"self/sawtoothWave\")");
        assertThat(coerced.get("targetVariable")).isEqualTo("waveSum");
        assertThat(coerced.containsKey("ruleJson")).isFalse();
    }

    @Test
    void configureAlertFlattensConfigJson() {
        Map<String, Object> coerced = AgentToolArgCoercion.coerce(
                "configure_alert",
                Map.of(
                        "name", "high-temp",
                        "configJson", Map.of(
                                "watchVariable", "temperature",
                                "conditionExpr", "self.temperature[\"value\"] > 80",
                                "targetObjectPath", "root.platform.devices.dev1",
                                "eventName", "highTemp"
                        )
                )
        );
        assertThat(coerced.get("path")).isEqualTo("root.platform.alert-rules.high-temp");
        assertThat(coerced.get("watchVariable")).isEqualTo("temperature");
        assertThat(coerced.get("eventName")).isEqualTo("highTemp");
        assertThat(coerced.containsKey("configJson")).isFalse();
    }

    @Test
    void addDashboardWidgetParsesWidgetJsonString() {
        Map<String, Object> coerced = AgentToolArgCoercion.coerce(
                "add_dashboard_widget",
                Map.of(
                        "dashboardPath", "root.platform.dashboards.main",
                        "widget", "{\"type\":\"value\",\"objectPath\":\"root.platform.devices.dev1\",\"variableName\":\"sineWave\"}"
                )
        );
        assertThat(coerced.get("path")).isEqualTo("root.platform.dashboards.main");
        assertThat(coerced.get("widget")).isInstanceOf(Map.class);
        @SuppressWarnings("unchecked")
        Map<String, Object> widget = (Map<String, Object>) coerced.get("widget");
        assertThat(widget.get("type")).isEqualTo("value");
    }

    @Test
    void deployTreeFunctionParsesSchemaJsonStrings() {
        Map<String, Object> coerced = AgentToolArgCoercion.coerce(
                "deploy_tree_function",
                Map.of(
                        "objectPath", "root.platform.apps.hub",
                        "inputSchema", "{\"fields\":[]}"
                )
        );
        assertThat(coerced.get("path")).isEqualTo("root.platform.apps.hub");
        assertThat(coerced.get("inputSchema")).isInstanceOf(Map.class);
    }
}
