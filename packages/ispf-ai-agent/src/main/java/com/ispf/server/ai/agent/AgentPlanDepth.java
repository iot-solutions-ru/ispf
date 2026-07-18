package com.ispf.server.ai.agent;

import java.util.Locale;

/**
 * LITE — short goal + steps; FULL — SIF 8-layer intake (FW-53).
 */
public enum AgentPlanDepth {
    LITE,
    FULL;

    public static AgentPlanDepth resolve(String userMessage, boolean textAttachment, boolean sessionDocuments) {
        if (textAttachment || sessionDocuments) {
            return FULL;
        }
        if (userMessage == null || userMessage.isBlank()) {
            return LITE;
        }
        String lower = userMessage.toLowerCase(Locale.ROOT);
        if (lower.contains("полный тз")
                || lower.contains("полное тз")
                || lower.contains("8 сло")
                || lower.contains("8-layer")
                || lower.contains("8 layer")
                || lower.contains("full tz")
                || lower.contains("full spec")) {
            return FULL;
        }
        return LITE;
    }
}
