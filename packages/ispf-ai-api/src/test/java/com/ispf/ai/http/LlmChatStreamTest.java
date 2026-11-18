package com.ispf.ai.http;

import com.ispf.ai.LlmException;
import com.ispf.ai.LlmResponse;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LlmChatStreamTest {

    @Test
    void openAiSseAccumulatesContentAndNotifiesPreview() throws Exception {
        String sse = """
                data: {"model":"demo","choices":[{"delta":{"content":"Hello"}}]}

                data: {"choices":[{"delta":{"content":" world"},"finish_reason":"stop"}]}

                data: [DONE]
                """;
        List<String> previews = new ArrayList<>();
        LlmResponse response = LlmChatStream.parseOpenAiSse(sse, "fallback", previews::add);
        assertEquals("Hello world", response.content());
        assertEquals("demo", response.model());
        assertEquals("stop", response.finishReason());
        assertEquals(List.of("Hello", "Hello world"), previews);
    }

    @Test
    void openAiSseAssemblesToolCallDeltas() throws Exception {
        String sse = """
                data: {"choices":[{"delta":{"tool_calls":[{"index":0,"id":"call_1","function":{"name":"list_objects","arguments":""}}]}}]}

                data: {"choices":[{"delta":{"tool_calls":[{"index":0,"function":{"arguments":"{\\"path\\":\\"root\\"}"}}]},"finish_reason":"tool_calls"}]}

                data: [DONE]
                """;
        List<String> previews = new ArrayList<>();
        LlmResponse response = LlmChatStream.parseOpenAiSse(sse, "fallback", previews::add);
        assertEquals(1, response.toolCalls().size());
        assertEquals("list_objects", response.toolCalls().get(0).name());
        assertEquals("{\"path\":\"root\"}", response.toolCalls().get(0).argumentsJson());
        assertTrue(previews.get(previews.size() - 1).contains("list_objects"));
        assertTrue(previews.get(previews.size() - 1).contains("root"));
    }

    @Test
    void ollamaNdjsonAccumulatesContent() throws Exception {
        String ndjson = """
                {"model":"llama","message":{"content":"Hi"},"done":false}
                {"message":{"content":"!"},"done":true}
                """;
        List<String> previews = new ArrayList<>();
        LlmResponse response = LlmChatStream.parseOllamaNdjson(ndjson, "fallback", previews::add);
        assertEquals("Hi!", response.content());
        assertEquals("llama", response.model());
        assertEquals(List.of("Hi", "Hi!"), previews);
    }

    @Test
    void cancelAbortsTheRead() {
        String sse = """
                data: {"choices":[{"delta":{"content":"Hello"}}]}

                data: {"choices":[{"delta":{"content":" more"}}]}

                data: [DONE]
                """;
        AtomicBoolean cancelled = new AtomicBoolean(false);
        LlmException ex = assertThrows(LlmException.class, () -> LlmChatStream.readOpenAiSse(
                new java.io.ByteArrayInputStream(sse.getBytes(StandardCharsets.UTF_8)),
                "fallback",
                preview -> cancelled.set(true),
                cancelled::get
        ));
        assertTrue(ex.getMessage().contains("cancelled"));
    }

    @Test
    void plainJsonBodyIsAcceptedWhenThePeerIgnoresSse() throws Exception {
        String json = """
                {"choices":[{"message":{"role":"assistant","content":"plain-ok"},"finish_reason":"stop"}]}
                """;
        LlmResponse response = LlmChatStream.parseOpenAiSse(json, "fallback", preview -> {
        });
        assertEquals("plain-ok", response.content());
        assertEquals("stop", response.finishReason());
    }

    @Test
    void providerErrorFrameFailsTheRead() {
        String sse = """
                data: {"error":{"message":"context length exceeded"}}

                """;
        LlmException ex = assertThrows(LlmException.class, () -> LlmChatStream.parseOpenAiSse(sse, "fallback", null));
        assertTrue(ex.getMessage().contains("context length exceeded"));
    }

    @Test
    void objectToolArgumentsAreKeptAndSecretsAreMaskedInPreview() throws Exception {
        String sse = """
                data: {"choices":[{"delta":{"tool_calls":[{"index":0,"id":"c1","function":{"name":"set_variable","arguments":{"path":"root","password":"hunter2"}}}]},"finish_reason":"tool_calls"}]}

                data: [DONE]
                """;
        List<String> previews = new ArrayList<>();
        LlmResponse response = LlmChatStream.parseOpenAiSse(sse, "fallback", previews::add);
        assertEquals("set_variable", response.toolCalls().get(0).name());
        assertTrue(response.toolCalls().get(0).argumentsJson().contains("hunter2"));
        assertTrue(previews.get(previews.size() - 1).contains("***"));
        assertTrue(!previews.get(previews.size() - 1).contains("hunter2"));
    }

    @Test
    void ollamaErrorLineFailsTheRead() {
        LlmException ex = assertThrows(
                LlmException.class,
                () -> LlmChatStream.parseOllamaNdjson("{\"error\":\"model not found\"}\n", "fallback", null)
        );
        assertTrue(ex.getMessage().contains("model not found"));
    }

    @Test
    void openSecretValueIsMaskedBeforeTheClosingQuote() throws Exception {
        String sse = """
                data: {"choices":[{"delta":{"tool_calls":[{"index":0,"id":"c1","function":{"name":"set_variable","arguments":"{\\"mqttPassword\\":\\"hun"}}]}}]}

                data: {"choices":[{"delta":{"tool_calls":[{"index":0,"function":{"arguments":"ter2\\"}"}}]},"finish_reason":"tool_calls"}]}

                data: [DONE]
                """;
        List<String> previews = new ArrayList<>();
        LlmResponse response = LlmChatStream.parseOpenAiSse(sse, "fallback", previews::add);
        assertTrue(response.toolCalls().get(0).argumentsJson().contains("hunter2"));
        assertTrue(!previews.isEmpty());
        for (String preview : previews) {
            assertTrue(!preview.contains("hun"), preview);
        }
    }

    @Test
    void trailingBackslashDoesNotExposeTheSecretPrefix() throws Exception {
        String sse = """
                data: {"choices":[{"delta":{"tool_calls":[{"index":0,"id":"c1","function":{"name":"set_variable","arguments":"{\\"privateKey\\":\\"AB\\\\"}}]}}]}

                data: {"choices":[{"delta":{"tool_calls":[{"index":0,"function":{"arguments":"C\\"}"}}]},"finish_reason":"tool_calls"}]}

                data: [DONE]
                """;
        List<String> previews = new ArrayList<>();
        LlmResponse response = LlmChatStream.parseOpenAiSse(sse, "fallback", previews::add);
        assertTrue(response.toolCalls().get(0).argumentsJson().contains("AB\\C"));
        for (String preview : previews) {
            assertTrue(!preview.contains("AB"), preview);
        }
    }

    @Test
    void newlineAndBackslashRunsInASecretValueMaskWithoutBacktracking() {
        String newlines = "{\"token\":\"" + "\n".repeat(5_000);
        String backslashes = "{\"secret\":\"" + "\\".repeat(5_001);
        assertTimeoutPreemptively(Duration.ofSeconds(2), () -> {
            assertEquals("{\"token\":\"***", LlmChatStream.redactedPreview(newlines));
            assertEquals("{\"secret\":\"***", LlmChatStream.redactedPreview(backslashes));
        });
    }

    @Test
    void secretValueCutByTheVisibleTailStaysMasked() {
        String text = "x".repeat(20_000) + "{\"apiKey\":\"" + "S".repeat(3_000) + "\"}" + "y".repeat(2_000);
        String preview = LlmChatStream.redactedPreview(text);
        assertTrue(preview.startsWith("…"));
        assertTrue(preview.length() <= 4_000);
        assertTrue(preview.contains("***\"}"));
        assertFalse(preview.contains("SSS"));
    }
}
