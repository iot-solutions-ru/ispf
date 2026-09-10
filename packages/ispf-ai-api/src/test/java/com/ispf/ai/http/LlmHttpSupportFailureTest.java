package com.ispf.ai.http;

import org.junit.jupiter.api.Test;

import java.nio.channels.ClosedChannelException;
import java.nio.channels.UnresolvedAddressException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LlmHttpSupportFailureTest {

    @Test
    void formatHttpFailureUsesExceptionTypeWhenMessageIsNull() {
        String message = LlmHttpSupport.formatHttpFailure(
                "http://lab-edge.example.invalid:8000/v1/chat/completions",
                new UnresolvedAddressException()
        );
        assertFalse(message.contains("failed: null"));
        assertTrue(message.contains("UnresolvedAddressException"));
        assertTrue(message.contains("lab-edge.example.invalid"));
    }

    @Test
    void formatHttpFailureUnwrapsClosedChannel() {
        String message = LlmHttpSupport.formatHttpFailure(
                "https://example.invalid/v1/chat/completions",
                new ClosedChannelException()
        );
        assertFalse(message.contains("failed: null"));
        assertTrue(message.contains("ClosedChannelException"));
    }

    @Test
    void formatHttpStatusFailureReplacesNginxHtmlWithHintAndUrl() {
        String html = """
                <html>
                <head><title>404 Not Found</title></head>
                <body>
                <center><h1>404 Not Found</h1></center>
                <hr><center>nginx</center>
                </body>
                </html>
                """;
        String message = LlmHttpSupport.formatHttpStatusFailure(
                "https://api.deepseek.com/chat/completions",
                404,
                html
        );
        assertFalse(message.contains("<html>"));
        assertTrue(message.contains("HTTP 404"));
        assertTrue(message.contains("nginx HTML 404"));
        assertTrue(message.contains("/v1"));
        assertTrue(message.contains("url=https://api.deepseek.com/chat/completions"));
    }

    @Test
    void formatHttpStatusFailureKeepsJsonBody() {
        String message = LlmHttpSupport.formatHttpStatusFailure(
                "https://api.deepseek.com/v1/chat/completions",
                401,
                "{\"error\":{\"message\":\"invalid api key\"}}"
        );
        assertTrue(message.contains("invalid api key"));
        assertTrue(message.contains("url=https://api.deepseek.com/v1/chat/completions"));
    }

    @Test
    void normalizeOpenAiCompatibleBaseUrlAddsV1AndStripsCompletionsPath() {
        assertEquals("https://api.deepseek.com/v1", LlmHttpSupport.normalizeOpenAiCompatibleBaseUrl("https://api.deepseek.com"));
        assertEquals("https://api.deepseek.com/v1", LlmHttpSupport.normalizeOpenAiCompatibleBaseUrl("https://api.deepseek.com/"));
        assertEquals("https://api.deepseek.com/v1", LlmHttpSupport.normalizeOpenAiCompatibleBaseUrl("https://api.deepseek.com/v1"));
        assertEquals(
                "https://api.deepseek.com/v1",
                LlmHttpSupport.normalizeOpenAiCompatibleBaseUrl("https://api.deepseek.com/v1/chat/completions")
        );
        assertEquals("https://openrouter.ai/api/v1", LlmHttpSupport.normalizeOpenAiCompatibleBaseUrl("https://openrouter.ai/api/v1"));
        assertEquals("http://127.0.0.1:8000/v1", LlmHttpSupport.normalizeOpenAiCompatibleBaseUrl("http://127.0.0.1:8000"));
        assertEquals("", LlmHttpSupport.normalizeOpenAiCompatibleBaseUrl("  "));
    }
}
