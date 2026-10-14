package com.ispf.server.ai.agent;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * ADR-0051: pre-finish judge must stay on the TreeFirst execute finish path.
 * Regression for the disconnect that left {@link AgentJudgeService} test-only.
 */
class AgentJudgeWireUpTest {

    @Test
    void treeFirstExecuteFinishInvokesJudgeService() throws Exception {
        Path path = Path.of("src/main/java/com/ispf/server/ai/agent/TreeFirstAgentService.java");
        String source = Files.readString(path, StandardCharsets.UTF_8);
        assertThat(source)
                .contains("AgentJudgeService.evaluate(")
                .contains("liveTreeSensors()")
                .contains("Judge REWORK")
                .contains("incrementReworkRound()");
    }
}
