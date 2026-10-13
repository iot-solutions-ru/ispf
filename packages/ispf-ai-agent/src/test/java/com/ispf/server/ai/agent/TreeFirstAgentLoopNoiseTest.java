package com.ispf.server.ai.agent;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TreeFirstAgentLoopNoiseTest {

    @Test
    void continueMessageUsesGuardHintNotDashboardDefault() {
        String message = TreeFirstAgentService.loopNoiseContinueMessage(
                "get_event_schema needs objectPath and eventName",
                "en"
        );
        assertTrue(message.contains("objectPath"));
        assertFalse(message.toLowerCase().contains("set_dashboard_layout"));
    }

    @Test
    void continueMessageFallsBackWithoutHint() {
        String message = TreeFirstAgentService.loopNoiseContinueMessage("  ", "ru");
        assertTrue(message.contains("Продолжай"));
        assertFalse(message.contains("set_dashboard_layout"));
    }
}
