package com.ispf.server.ai.agent;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

class AgentSessionHistoryTest {

    @Test
    void replaysRecentTurnsForLlmContext() {
        AgentSession session = AgentSession.create("admin", "root");
        session.addTurn(AgentTurn.create("first task", "first done", "OK", List.of(), Map.of()));
        session.addTurn(AgentTurn.create("second task", "second done", "OK", List.of(), Map.of()));

        assertEquals(2, session.turns().size());
        assertEquals("first task", session.turns().get(0).userMessage());
        assertEquals("second done", session.turns().get(1).assistantSummary());
    }
}
