package com.ispf.server.ai.agent;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

class ReferenceScenarioTest {

    static Stream<ReferenceScenarioCatalog.ReferenceScenario> scenarios() {
        return ReferenceScenarioCatalog.all().stream();
    }

    @Test
    void catalogContainsTenScenarios() {
        assertThat(ReferenceScenarioCatalog.all()).hasSize(10);
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("scenarios")
    void scenarioClassifiesExpectedAssignmentType(ReferenceScenarioCatalog.ReferenceScenario scenario) {
        var classification = AgentAssignmentClassifier.classify(scenario.prompt());
        AgentAssignmentType expected = AgentAssignmentType.valueOf(scenario.assignmentType());
        assertThat(classification.type()).isEqualTo(expected);
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("scenarios")
    void scenarioPlanPayloadIsStructured(ReferenceScenarioCatalog.ReferenceScenario scenario) {
        Map<String, Object> finish = scenario.planFinishResult();
        assertThat(AgentPlanGuard.isPlanFinish(finish)).isTrue();
        assertThat(AgentPlanGuard.hasStructuredPlanContent(finish, new AgentRunState())).isTrue();
        assertThat(scenario.planSteps()).isNotEmpty();
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("scenarios")
    void scenarioValidateToolsAreReadOnly(ReferenceScenarioCatalog.ReferenceScenario scenario) {
        for (String tool : scenario.validateTools()) {
            assertThat(AgentPlanGuard.isReadOnlyTool(tool))
                    .as("validate tool %s in %s", tool, scenario.id())
                    .isTrue();
        }
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("scenarios")
    void scenarioMutationsBlockedUntilApproval(ReferenceScenarioCatalog.ReferenceScenario scenario) {
        AgentRunState state = new AgentRunState();
        state.setPlanPhase(AgentPlanPhase.AWAITING_APPROVAL);
        String mutatingTool = scenario.planSteps().stream()
                .filter(tool -> !AgentPlanGuard.isReadOnlyTool(tool))
                .findFirst()
                .orElse("create_object");

        var block = AgentMutateApprovalGuard.checkBeforeTool(true, state, mutatingTool, AgentProfile.ADMIN);
        assertThat(block).isPresent();
        assertThat(block.get().error()).contains("approval");

        AgentPlanGuard.beginTurn(state, "Утверждаю план, начинай выполнение", AgentProfile.ADMIN, true, "tester");
        assertThat(state.isPlanApproved()).isTrue();
        assertThat(state.planApprovedBy()).isEqualTo("tester");

        var afterApprove = AgentMutateApprovalGuard.checkBeforeTool(true, state, mutatingTool, AgentProfile.ADMIN);
        assertThat(afterApprove).isEmpty();
    }

    @Test
    void helpEntriesMatchCatalogSize() {
        assertThat(ReferenceScenarioCatalog.helpEntries()).hasSize(10);
    }
}
