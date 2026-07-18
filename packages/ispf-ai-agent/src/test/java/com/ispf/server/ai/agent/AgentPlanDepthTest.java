package com.ispf.server.ai.agent;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class AgentPlanDepthTest {

    @Test
    void defaultsToLite() {
        assertThat(AgentPlanDepth.resolve("Создай насосную", false, false)).isEqualTo(AgentPlanDepth.LITE);
    }

    @Test
    void fullWhenAttachmentOrKeywords() {
        assertThat(AgentPlanDepth.resolve("план", true, false)).isEqualTo(AgentPlanDepth.FULL);
        assertThat(AgentPlanDepth.resolve("нужен полный ТЗ", false, false)).isEqualTo(AgentPlanDepth.FULL);
        assertThat(AgentPlanDepth.resolve("plan", false, true)).isEqualTo(AgentPlanDepth.FULL);
    }
}
