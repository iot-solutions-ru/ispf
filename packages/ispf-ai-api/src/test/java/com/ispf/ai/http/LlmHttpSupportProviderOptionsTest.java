package com.ispf.ai.http;

import com.fasterxml.jackson.databind.node.ObjectNode;
import com.ispf.ai.LlmMessage;
import com.ispf.ai.LlmRequest;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

class LlmHttpSupportProviderOptionsTest {

    @Test
    void mergesProviderOptionsIntoChatBody() {
        LlmRequest request = new LlmRequest(
                "test-model",
                List.of(new LlmMessage("user", "hello")),
                128,
                0.1,
                Map.of("chat_template_kwargs", Map.of("enable_thinking", false))
        );
        ObjectNode body = LlmHttpSupport.chatCompletionBody(request);
        assertFalse(body.path("chat_template_kwargs").path("enable_thinking").asBoolean(true));
        assertEquals("test-model", body.path("model").asText());
    }
}
