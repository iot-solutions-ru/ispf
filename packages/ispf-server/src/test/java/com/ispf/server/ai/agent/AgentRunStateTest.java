package com.ispf.server.ai.agent;

import org.junit.jupiter.api.Test;

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
                executor.submit(() -> {
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
}
