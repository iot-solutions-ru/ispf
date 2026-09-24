package com.ispf.server.ai.agent;

import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Finish adapter over {@link AcceptanceVerdict}. The verdict is the policy;
 * this type keeps the block shape the agent loop already records.
 */
final class AgentPlatformTurnGuard {

    private AgentPlatformTurnGuard() {
    }

    record BlockDecision(String error, String hint, String checkId, String acceptanceStatus) {
        boolean blocked() {
            return error != null && !error.isBlank();
        }
    }

    static Optional<BlockDecision> checkBeforeFinish(List<Map<String, Object>> steps) {
        return checkBeforeFinish(steps, "");
    }

    static Optional<BlockDecision> checkBeforeFinish(List<Map<String, Object>> steps, String userMessage) {
        return checkBeforeFinish(steps, userMessage, null);
    }

    static Optional<BlockDecision> checkBeforeFinish(
            List<Map<String, Object>> steps,
            String userMessage,
            String assignmentType
    ) {
        return blockIfNeeded(AcceptanceVerdict.evaluate(steps, userMessage, assignmentType));
    }

    static Optional<BlockDecision> blockIfNeeded(AcceptanceVerdict.Result verdict) {
        if (verdict == null || verdict.allowsFinish()) {
            return Optional.empty();
        }
        AcceptanceVerdict.Check check = verdict.firstBlocking();
        if (check == null) {
            return Optional.empty();
        }
        return Optional.of(new BlockDecision(
                check.summary(),
                check.hint(),
                check.id(),
                check.status().name()
        ));
    }

    /** Same guard error repeated this many times → turn is stuck (safety net). */
    static int countRepeatedGuardError(List<Map<String, Object>> steps, String error) {
        if (error == null || error.isBlank() || steps == null) {
            return 0;
        }
        int count = 0;
        for (Map<String, Object> step : steps) {
            if ("guard".equals(String.valueOf(step.get("type")))
                    && error.equals(String.valueOf(step.get("error")))) {
                count++;
            }
        }
        return count;
    }

    static boolean isStuckGuardLoop(List<Map<String, Object>> steps, String error) {
        return countRepeatedGuardError(steps, error) >= 3;
    }
}
