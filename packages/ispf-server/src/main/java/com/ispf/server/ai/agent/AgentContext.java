package com.ispf.server.ai.agent;

import org.springframework.security.core.Authentication;

public record AgentContext(
        String actor,
        Authentication authentication,
        AgentRunState runState
) {
}
