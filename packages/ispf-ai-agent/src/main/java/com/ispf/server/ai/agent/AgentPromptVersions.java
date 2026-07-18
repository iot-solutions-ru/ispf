package com.ispf.server.ai.agent;

/**
 * Prompt template versions written to audit for regression tracking (FW-52).
 */
public final class AgentPromptVersions {

    public static final String ADMIN_AGENT = "admin-agent-v2.1";
    public static final String ASK_AGENT = "ask-agent-v1.1";
    public static final String OPERATOR_AGENT = "operator-agent-v1.0";

    private AgentPromptVersions() {
    }

    public static String profileFor(AgentProfile profile, AgentInteractionMode mode) {
        if (profile == AgentProfile.OPERATOR) {
            return OPERATOR_AGENT;
        }
        if (mode == AgentInteractionMode.ASK) {
            return ASK_AGENT;
        }
        return ADMIN_AGENT;
    }
}
