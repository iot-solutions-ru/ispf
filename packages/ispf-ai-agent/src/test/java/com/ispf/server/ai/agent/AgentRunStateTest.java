package com.ispf.server.ai.agent;

import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AgentRunStateTest {

    @Test
    void incrementReworkRoundIsThreadSafe() throws Exception {
        AgentRunState state = new AgentRunState();
        int threads = 8;
        int incrementsPerThread = 1_000;
        ExecutorService executor = Executors.newFixedThreadPool(threads);
        CountDownLatch start = new CountDownLatch(1);
        CountDownLatch done = new CountDownLatch(threads);

        try {
            for (int i = 0; i < threads; i++) {
                var _ = executor.submit(() -> {
                    try {
                        start.await(5, TimeUnit.SECONDS);
                        for (int j = 0; j < incrementsPerThread; j++) {
                            state.incrementReworkRound();
                        }
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    } finally {
                        done.countDown();
                    }
                });
            }

            start.countDown();
            assertTrue(done.await(5, TimeUnit.SECONDS));
            assertEquals(threads * incrementsPerThread, state.reworkRoundCount());
        } finally {
            executor.shutdownNow();
        }
    }

    @Test
    void resetReworkRoundsClearsCount() {
        AgentRunState state = new AgentRunState();
        state.incrementReworkRound();
        state.incrementReworkRound();

        state.resetReworkRounds();

        assertEquals(0, state.reworkRoundCount());
    }

    @Test
    void snapshotRestoresClientChannelAndFocus() {
        AgentRunState original = new AgentRunState();
        original.setClientChannel("copilot");
        original.setUiLocale("ru");
        original.setClientFocus(Map.of(
                "surface", "system",
                "detail", Map.of(
                        "settingsTab", "ai",
                        "visibleSettingIds", List.of("ai.enabled", "ai.model")
                )
        ));

        AgentRunState restored = new AgentRunState();
        restored.restore(JsonMapper.builder().build(), original.snapshot(JsonMapper.builder().build()));

        assertEquals("copilot", restored.clientChannel());
        assertEquals("ru", restored.uiLocale());
        assertEquals("system", restored.clientFocus().get("surface"));
        assertTrue(AgentCopilotPromptBuilder.isSettingsScreenFocus(restored.clientFocus()));
    }
}
