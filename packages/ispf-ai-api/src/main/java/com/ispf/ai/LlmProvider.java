package com.ispf.ai;

import com.ispf.ai.http.LlmChatStream;

import java.util.List;

/**
 * Service Provider Interface for LLM backends (OpenAI-compatible, Ollama, custom URL).
 * Implementations live outside {@code ispf-server} core.
 */
public interface LlmProvider {

    String providerId();

    boolean isAvailable();

    List<LlmModelInfo> listModels() throws LlmException;

    LlmResponse complete(LlmRequest request) throws LlmException;

    /**
     * Stream tokens when the backend supports it. Default waits for the full completion
     * and emits the finished text once.
     */
    default LlmResponse completeStreaming(
            LlmRequest request,
            java.util.function.Consumer<String> onPreview,
            java.util.function.BooleanSupplier cancelled
    ) throws LlmException {
        if (cancelled != null && cancelled.getAsBoolean()) {
            throw new LlmException("Agent run cancelled");
        }
        LlmResponse response = complete(request);
        if (cancelled != null && cancelled.getAsBoolean()) {
            throw new LlmException("Agent run cancelled");
        }
        if (onPreview != null && response != null && response.content() != null && !response.content().isBlank()) {
            onPreview.accept(LlmChatStream.redactedPreview(response.content()));
        }
        return response;
    }

    /**
     * Checks whether {@code model} accepts image input (provider metadata or live probe).
     */
    boolean supportsVision(String model) throws LlmException;

    default boolean supportsToolCalling(String model) {
        return false;
    }
}
