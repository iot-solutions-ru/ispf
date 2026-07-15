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
                        Map.of("name", "list_binding_rules", "description", "Rules")
                ),
                false
        );
        assertThat(prompt).contains("HERE-AND-NOW");
        assertThat(prompt).contains("NOT AI Studio");
        assertThat(prompt).contains("list_variables");
        assertThat(prompt).contains("list_binding_rules");
        assertThat(prompt).doesNotContain("create_object");
        assertThat(prompt).doesNotContain("Reference playbooks");
        assertThat(prompt).doesNotContain("PLAN-BEFORE-EXECUTE");
    }

    @Test
    void filterToolsKeepsOnlyAllowed() {
        var filtered = AgentCopilotPromptBuilder.filterTools(List.of(
                Map.of("name", "describe_variables", "description", "d"),
                Map.of("name", "import_package", "description", "mutate")
        ));
        assertThat(filtered).extracting(m -> m.get("name"))
                .containsExactly("describe_variables");
    }
}
