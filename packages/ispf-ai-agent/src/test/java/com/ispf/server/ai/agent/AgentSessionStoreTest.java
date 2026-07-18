package com.ispf.server.ai.agent;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest
@ActiveProfiles("test")
class AgentSessionStoreTest {

    @Autowired
    private AgentSessionStore store;

    @Test
    void createGetPersistAndDeleteSession() {
        AgentSession created = store.create("admin", "root");
        assertTrue(store.get(created.sessionId(), "admin").isPresent());
        assertFalse(store.get(created.sessionId(), "other").isPresent());

        created.addTurn(AgentTurn.create("hello", "done", "OK", List.of(), Map.of()));
        store.persistAfterTurn(created, created.turns().getLast());

        AgentSession reloaded = store.get(created.sessionId(), "admin").orElseThrow();
        assertEquals("hello", reloaded.title());
        assertEquals(1, reloaded.turns().size());

        assertTrue(store.delete(created.sessionId(), "admin"));
        assertFalse(store.get(created.sessionId(), "admin").isPresent());
    }

    @Test
    void truncatesTitleOnFirstTurn() {
        AgentSession session = store.create("admin", "root");
        AgentTurn turn = AgentTurn.create(
                "Создай SNMP localhost с метриками CPU RAM network dashboard",
                "ok",
                "OK",
                List.of(),
                Map.of()
        );
        session.addTurn(turn);
        store.persistAfterTurn(session, turn);
        AgentSession reloaded = store.get(session.sessionId(), "admin").orElseThrow();
        assertTrue(reloaded.title().length() <= 60);
        assertFalse(reloaded.title().equals("New chat"));
    }
}
