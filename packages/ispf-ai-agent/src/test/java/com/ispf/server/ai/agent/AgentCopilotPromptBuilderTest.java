package com.ispf.server.ai.agent;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class AgentCopilotPromptBuilderTest {

    @Test
    void buildIsNotAskStudioPlaybook() {
        String prompt = AgentCopilotPromptBuilder.build(
                "root",
                List.of(
                        Map.of("name", "list_variables", "description", "List vars"),
                        Map.of("name", "create_object", "description", "Create"),
                        Map.of("name", "list_binding_rules", "description", "Rules"),
                        Map.of("name", "get_widget_catalog", "description", "Widgets"),
                        Map.of("name", "add_dashboard_widget", "description", "Add widget")
                ),
                false
        );
        assertThat(prompt).contains("HERE-AND-NOW");
        assertThat(prompt).contains("NOT AI Studio");
        assertThat(prompt).contains("understand first");
        assertThat(prompt).contains("list_variables");
        assertThat(prompt).contains("list_binding_rules");
        assertThat(prompt).contains("get_widget_catalog");
        assertThat(prompt).contains("add_dashboard_widget");
        assertThat(prompt).contains("ruleKind=historian");
        assertThat(prompt).doesNotContain("create_object");
        assertThat(prompt).doesNotContain("Reference playbooks");
        assertThat(prompt).doesNotContain("PLAN-BEFORE-EXECUTE");
        assertThat(prompt).doesNotContain("- get_automation_schema:");
    }

    @Test
    void filterToolsKeepsDashboardAndBindingHelpers() {
        var filtered = AgentCopilotPromptBuilder.filterTools(List.of(
                Map.of("name", "describe_variables", "description", "d"),
                Map.of("name", "add_dashboard_widget", "description", "w"),
                Map.of("name", "create_binding_rule", "description", "b"),
                Map.of("name", "configure_variable_history", "description", "h"),
                Map.of("name", "import_package", "description", "mutate"),
                Map.of("name", "get_automation_schema", "description", "schema")
        ));
        assertThat(filtered).extracting(m -> m.get("name"))
                .containsExactly("describe_variables", "add_dashboard_widget", "create_binding_rule", "configure_variable_history");
    }

    @Test
    void settingsFocusHidesDiscoveryTools() {
        Map<String, Object> focus = Map.of(
                "surface", "system",
                "detail", Map.of(
                        "systemTab", "settings",
                        "settingsTab", "ai",
                        "visibleSettingIds", List.of("ai.enabled", "ai.model")
                )
        );
        var filtered = AgentCopilotPromptBuilder.filterTools(List.of(
                Map.of("name", "list_objects", "description", "o"),
                Map.of("name", "search_context", "description", "s"),
                Map.of("name", "get_automation_schema", "description", "g"),
                Map.of("name", "create_binding_rule", "description", "b")
        ), focus);
        assertThat(filtered).extracting(m -> m.get("name")).containsExactly("create_binding_rule");
        assertThat(AgentCopilotPromptBuilder.isAllowedTool("get_automation_schema", focus)).isFalse();
        assertThat(AgentCopilotPromptBuilder.isAllowedTool("search_context", focus)).isFalse();
        assertThat(AgentCopilotPromptBuilder.isAllowedTool("list_objects", focus)).isFalse();
    }
}
