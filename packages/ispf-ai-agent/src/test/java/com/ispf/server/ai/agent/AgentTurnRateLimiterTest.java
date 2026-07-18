package com.ispf.server.ai.agent;

import com.ispf.server.config.AiProperties;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AgentTurnRateLimiterTest {

    @Test
    void blocksConcurrentTurnsBeyondLimit() {
        AiProperties properties = new AiProperties();
        properties.setAgentMaxConcurrentTurnsPerUser(1);
        properties.setAgentMaxTurnsPerHourPerUser(10);
        AgentTurnRateLimiter limiter = new AgentTurnRateLimiter(properties);

        limiter.acquire("alice");
        assertThatThrownBy(() -> limiter.acquire("alice"))
                .isInstanceOf(AgentTurnRateLimiter.AgentRateLimitException.class);

        limiter.release("alice");
        assertThatCode(() -> limiter.acquire("alice")).doesNotThrowAnyException();
        limiter.release("alice");
    }
}
