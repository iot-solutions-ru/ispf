package com.ispf.server.ai.agent;

import org.springframework.security.core.Authentication;

public record AgentContext(
        String actor,
        Authentication authentication,
        AgentRunState runState,
        AgentProfile profile,
        OperatorAgentScope operatorScope,
        String sessionId
) {
    public AgentContext(String actor, Authentication authentication, AgentRunState runState) {
        this(actor, authentication, runState, AgentProfile.ADMIN, null, null);
    }

    public AgentContext(
            String actor,
            Authentication authentication,
            AgentRunState runState,
            AgentProfile profile,
            OperatorAgentScope operatorScope
    ) {
        this(actor, authentication, runState, profile, operatorScope, null);
    }

    public boolean isOperator() {
        return profile == AgentProfile.OPERATOR;
    }
}
