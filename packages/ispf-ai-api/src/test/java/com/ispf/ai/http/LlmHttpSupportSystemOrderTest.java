package com.ispf.ai.http;

import com.fasterxml.jackson.databind.JsonNode;
import com.ispf.ai.LlmMessage;
import com.ispf.ai.LlmRequest;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LlmHttpSupportSystemOrderTest {

    @Test
    void foldsTrailingSystemIntoLeadingSystem() {
        List<LlmMessage> normalized = LlmHttpSupport.normalizeMessagesForOpenAiCompatible(List.of(
                new LlmMessage("system", "You are Copilot."),
                new LlmMessage("user", "history"),
                new LlmMessage("assistant", "ok"),
                new LlmMessage("system", "LIVE UI SNAPSHOT"),
                new LlmMessage("user", "Explain this screen")
        ));

        assertEquals(4, normalized.size());
        assertEquals("system", normalized.get(0).role());
        assertTrue(normalized.get(0).content().contains("You are Copilot."));
        assertTrue(normalized.get(0).content().contains("LIVE UI SNAPSHOT"));
        assertEquals(1, normalized.stream().filter(m -> "system".equals(m.role())).count());
        assertEquals("history", normalized.get(1).content());
        assertEquals("Explain this screen", normalized.get(3).content());
    }

    @Test
    void chatCompletionBodyEmitsSingleLeadingSystem() {
        var body = LlmHttpSupport.chatCompletionBody(new LlmRequest(
                "qwen",
                List.of(
                        new LlmMessage("system", "A"),
                        new LlmMessage("user", "u1"),
                        new LlmMessage("system", "B"),
                        new LlmMessage("user", "u2")
                ),
                256,
                0.2,
                null,
                List.of(),
                null
        ));
        JsonNode messages = body.get("messages");
        assertEquals(3, messages.size());
        assertEquals("system", messages.get(0).get("role").asText());
        String system = messages.get(0).get("content").asText();
        assertTrue(system.contains("A"));
        assertTrue(system.contains("B"));
        assertEquals("user", messages.get(1).get("role").asText());
        assertEquals("user", messages.get(2).get("role").asText());
    }
}
