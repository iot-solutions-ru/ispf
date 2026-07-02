package com.ispf.server.ai.agent;

import java.util.Locale;
import java.util.Optional;

/**
 * BL-106: when {@code ispf.ai.agent-require-approval-for-mutate} is enabled, all non-read-only
 * platform tools require an approved plan before execution (admin profile only).
 */
final class AgentMutateApprovalGuard {

    private AgentMutateApprovalGuard() {
    }

    record BlockDecision(String error, String hint) {
        boolean blocked() {
            return error != null && !error.isBlank();
        }
    }

    static Optional<BlockDecision> checkBeforeTool(
            boolean requireApprovalForMutate,
            AgentRunState runState,
            String toolName,
            AgentProfile profile
    ) {
        if (!requireApprovalForMutate || profile == AgentProfile.OPERATOR || runState == null) {
            return Optional.empty();
        }
        if (AgentPlanGuard.isReadOnlyTool(toolName)) {
            return Optional.empty();
        }
        if (runState.isPlanApproved()) {
            return Optional.empty();
        }
        String normalized = toolName != null ? toolName.trim().toLowerCase(Locale.ROOT) : "";
        String hint = runState.isPlanningActive()
                ? "Approve the plan in the chat panel, then retry mutations."
                : "Request a plan (or switch to Plan mode), approve it, then mutations can run.";
        return Optional.of(new BlockDecision(
                "Tool '" + normalized + "' requires explicit plan approval before mutating the platform.",
                hint
        ));
    }
}
