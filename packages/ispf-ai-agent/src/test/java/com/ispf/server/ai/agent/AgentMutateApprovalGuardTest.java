package com.ispf.server.ai.agent;

import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class AgentMutateApprovalGuardTest {

    @Test
    void executeModeBypassesMutateApprovalGuard() {
        AgentRunState state = new AgentRunState();
        state.setInteractionMode(AgentInteractionMode.EXECUTE);
        assertThat(AgentMutateApprovalGuard.checkBeforeTool(true, state, "application_data_migrate", AgentProfile.ADMIN))
                .isEmpty();
        assertThat(AgentMutateApprovalGuard.checkBeforeTool(true, state, "create_object", AgentProfile.ADMIN))
                .isEmpty();
    }

    @Test
    void blocksCreateObjectWithoutApproval() {
        AgentRunState state = new AgentRunState();
        Optional<AgentMutateApprovalGuard.BlockDecision> block =
                AgentMutateApprovalGuard.checkBeforeTool(true, state, "create_object", AgentProfile.ADMIN);
        assertThat(block).isPresent();
        assertThat(block.get().error()).contains("create_object");
    }

    @Test
    void blocksDeployWithoutApproval() {
        AgentRunState state = new AgentRunState();
        Optional<AgentMutateApprovalGuard.BlockDecision> block =
                AgentMutateApprovalGuard.checkBeforeTool(true, state, "deploy_bundle", AgentProfile.ADMIN);
        assertThat(block).isPresent();
    }

    @Test
    void allowsReadOnlyToolsWithoutApproval() {
        AgentRunState state = new AgentRunState();
        assertThat(AgentMutateApprovalGuard.checkBeforeTool(true, state, "list_objects", AgentProfile.ADMIN))
                .isEmpty();
        assertThat(AgentMutateApprovalGuard.checkBeforeTool(true, state, "dry_run_deploy", AgentProfile.ADMIN))
                .isEmpty();
    }

    @Test
    void allowsMutationsAfterPlanApproved() {
        AgentRunState state = new AgentRunState();
        state.approvePlan("alice");
        assertThat(AgentMutateApprovalGuard.checkBeforeTool(true, state, "create_object", AgentProfile.ADMIN))
                .isEmpty();
    }

    @Test
    void allowsMutationsAfterCompleteExecutionResetsPlanPhase() {
        AgentRunState state = new AgentRunState();
        state.approvePlan("alice");
        AgentPlanGuard.completeExecution(state);
        assertThat(state.isPlanApproved()).isFalse();
        assertThat(state.isMutationsUnlockedForTurn()).isTrue();
        assertThat(AgentMutateApprovalGuard.checkBeforeTool(true, state, "save_mimic_diagram", AgentProfile.ADMIN))
                .isEmpty();
    }

    @Test
    void blocksMutationsOnNewTurnUntilApproval() {
        AgentRunState state = new AgentRunState();
        state.approvePlan("alice");
        AgentPlanGuard.beginTurn(state, "продолжай с мнемосхемой", AgentProfile.ADMIN, true, "alice");
        assertThat(AgentMutateApprovalGuard.checkBeforeTool(true, state, "save_mimic_diagram", AgentProfile.ADMIN))
                .isPresent();
    }

    @Test
    void disabledGuardAllowsMutations() {
        AgentRunState state = new AgentRunState();
        assertThat(AgentMutateApprovalGuard.checkBeforeTool(false, state, "create_object", AgentProfile.ADMIN))
                .isEmpty();
    }

    @Test
    void operatorProfileBypassesGuard() {
        AgentRunState state = new AgentRunState();
        assertThat(AgentMutateApprovalGuard.checkBeforeTool(true, state, "create_object", AgentProfile.OPERATOR))
                .isEmpty();
    }
}
