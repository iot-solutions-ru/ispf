package com.ispf.server.ai.agent;

import com.ispf.server.config.AiProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AgentSessionStoreTest {

    private AgentSessionStore store;

    @BeforeEach
    void setUp() {
        AiProperties properties = new AiProperties();
        properties.setAgentSessionTtlHours(24);
        store = new AgentSessionStore(properties);
    }

    @Test
    void createGetAndDeleteSession() {
        AgentSession created = store.create("admin", "root");
        assertTrue(store.get(created.sessionId(), "admin").isPresent());
        assertFalse(store.get(created.sessionId(), "other").isPresent());

        created.addTurn(AgentTurn.create("hello", "done", "OK", List.of(), Map.of()));
        assertEquals("hello", store.get(created.sessionId(), "admin").orElseThrow().title());

        assertTrue(store.delete(created.sessionId(), "admin"));
        assertFalse(store.get(created.sessionId(), "admin").isPresent());
    }

    @Test
    void truncatesTitleOnFirstTurn() {
        AgentSession session = store.create("admin", "root");
        session.addTurn(AgentTurn.create(
                "Создай SNMP localhost с метриками CPU RAM network dashboard",
                "ok",
                "OK",
                List.of(),
                Map.of()
        ));
        assertTrue(session.title().length() <= 60);
        assertFalse(session.title().equals("New chat"));
    }
}
