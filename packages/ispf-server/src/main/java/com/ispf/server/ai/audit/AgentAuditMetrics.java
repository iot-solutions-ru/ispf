package com.ispf.server.ai.audit;

import com.ispf.ai.LlmUsage;

public record AgentAuditMetrics(
        Long latencyMs,
        Integer promptTokens,
        Integer completionTokens,
        String turnId,
        Integer stepNo,
        String interactionMode,
        String promptProfile
) {
    public static AgentAuditMetrics empty() {
        return new AgentAuditMetrics(null, null, null, null, null, null, null);
    }

    public static AgentAuditMetrics of(
            Long latencyMs,
            LlmUsage usage,
            String turnId,
            Integer stepNo,
            String interactionMode,
            String promptProfile
    ) {
        return new AgentAuditMetrics(
                latencyMs,
                usage != null ? usage.promptTokens() : null,
                usage != null ? usage.completionTokens() : null,
                turnId,
                stepNo,
                interactionMode,
                promptProfile
        );
    }
}
