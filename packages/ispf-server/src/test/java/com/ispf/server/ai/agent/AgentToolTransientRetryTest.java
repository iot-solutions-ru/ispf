package com.ispf.server.ai.agent;

import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.concurrent.TimeoutException;

import static org.assertj.core.api.Assertions.assertThat;

class AgentToolTransientRetryTest {

    @Test
    void detectsTransientExceptionsAndErrors() {
        assertThat(AgentToolTransientRetry.isTransient(new TimeoutException("read timed out"))).isTrue();
        assertThat(AgentToolTransientRetry.isTransient(new IllegalStateException("connection refused"))).isTrue();
        assertThat(AgentToolTransientRetry.isTransient(new IllegalArgumentException("path is required"))).isFalse();

        assertThat(AgentToolTransientRetry.isTransientFailure(Map.of(
                "status", "ERROR",
                "error", "upstream 503 Unavailable"
        ))).isTrue();
        assertThat(AgentToolTransientRetry.isTransientFailure(Map.of(
                "status", "ERROR",
                "error", "path is required"
        ))).isFalse();
        assertThat(AgentToolTransientRetry.isTransientFailure(Map.of("status", "OK"))).isFalse();
    }
}
