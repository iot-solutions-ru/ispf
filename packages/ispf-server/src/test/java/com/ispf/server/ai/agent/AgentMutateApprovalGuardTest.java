package com.ispf.server.ai.agent;

import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class AgentMutateApprovalGuardTest {

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
