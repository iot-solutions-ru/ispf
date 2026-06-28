package com.ispf.server.ai.agent;

import org.springframework.security.core.Authentication;

public record AgentContext(
        String actor,
        Authentication authentication,
        AgentRunState runState,
        AgentProfile profile,
        OperatorAgentScope operatorScope
) {
    public AgentContext(String actor, Authentication authentication, AgentRunState runState) {
        this(actor, authentication, runState, AgentProfile.ADMIN, null);
    }

    public boolean isOperator() {
        return profile == AgentProfile.OPERATOR;
    }
}
