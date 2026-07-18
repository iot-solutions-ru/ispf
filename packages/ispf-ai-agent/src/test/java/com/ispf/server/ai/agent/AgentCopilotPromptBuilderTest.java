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
        assertThat(prompt).contains("list_variables");
        assertThat(prompt).contains("list_binding_rules");
        assertThat(prompt).contains("get_widget_catalog");
        assertThat(prompt).contains("add_dashboard_widget");
        assertThat(prompt).contains("ruleKind=historian");
        assertThat(prompt).doesNotContain("create_object");
        assertThat(prompt).doesNotContain("Reference playbooks");
        assertThat(prompt).doesNotContain("PLAN-BEFORE-EXECUTE");
    }

    @Test
    void filterToolsKeepsDashboardAndBindingHelpers() {
        var filtered = AgentCopilotPromptBuilder.filterTools(List.of(
                Map.of("name", "describe_variables", "description", "d"),
                Map.of("name", "add_dashboard_widget", "description", "w"),
                Map.of("name", "create_binding_rule", "description", "b"),
                Map.of("name", "configure_variable_history", "description", "h"),
                Map.of("name", "import_package", "description", "mutate")
        ));
        assertThat(filtered).extracting(m -> m.get("name"))
                .containsExactly("describe_variables", "add_dashboard_widget", "create_binding_rule", "configure_variable_history");
    }
}
