package com.ispf.server.ai.audit;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class AgentAuditMetricsTest {

    @Test
    void emptyMetricsHaveNullFields() {
        AgentAuditMetrics metrics = AgentAuditMetrics.empty();
        assertThat(metrics.latencyMs()).isNull();
        assertThat(metrics.turnId()).isNull();
    }

    @Test
    void ofMapsUsageAndTiming() {
        AgentAuditMetrics metrics = AgentAuditMetrics.of(
                120L,
                new com.ispf.ai.LlmUsage(10, 20, 30),
                "turn-1",
                3,
                "ask",
                "ask-agent-v1.1"
        );
        assertThat(metrics.latencyMs()).isEqualTo(120L);
        assertThat(metrics.promptTokens()).isEqualTo(10);
        assertThat(metrics.completionTokens()).isEqualTo(20);
        assertThat(metrics.turnId()).isEqualTo("turn-1");
        assertThat(metrics.stepNo()).isEqualTo(3);
    }
}
