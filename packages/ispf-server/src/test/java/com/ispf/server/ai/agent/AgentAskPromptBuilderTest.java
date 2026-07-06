package com.ispf.server.ai.agent;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class AgentAskPromptBuilderTest {

    @Test
    void buildExcludesPlanningInstructions() {
        String prompt = AgentAskPromptBuilder.build("root", sampleCatalog(), "", false);
        assertThat(prompt).contains("ASK mode");
        assertThat(prompt).doesNotContain("PLAN-BEFORE-EXECUTE");
        assertThat(prompt).doesNotContain("specIntakeGuide");
        assertThat(prompt).doesNotContain("Утвердить полный план");
    }

    @Test
    void readOnlyToolCatalogFiltersMutations() {
        var filtered = AgentAskPromptBuilder.readOnlyToolCatalog(sampleCatalog());
        assertThat(filtered).extracting(m -> m.get("name"))
                .contains("list_objects", "get_example_bundle", "validate_bundle")
                .doesNotContain("create_object", "import_package");
    }

    @Test
    void askModeBeginTurnClearsStoredPlan() {
        AgentRunState state = new AgentRunState();
        state.setStoredPlan(Map.of("goal", "Old plan", "steps", List.of("1. create_object")));
        state.setPlanPhase(AgentPlanPhase.AWAITING_APPROVAL);
        state.setInteractionMode(AgentInteractionMode.ASK);
        AgentPlanGuard.beginTurn(state, "Как упаковать bundle?", AgentProfile.ADMIN);
        assertThat(state.planPhase()).isEqualTo(AgentPlanPhase.NONE);
        assertThat(state.storedPlan()).isEmpty();
        assertThat(state.isPlanningActive()).isFalse();
    }

    private static List<Map<String, Object>> sampleCatalog() {
        return List.of(
                Map.of("name", "list_objects", "description", "List children"),
                Map.of("name", "get_example_bundle", "description", "Example manifest"),
                Map.of("name", "validate_bundle", "description", "Validate manifest"),
                Map.of("name", "create_object", "description", "Create node"),
                Map.of("name", "import_package", "description", "Deploy bundle")
        );
    }
}
