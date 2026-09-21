package com.ispf.ai;

import java.util.List;

public record LlmResponse(
        String content,
        String model,
        LlmUsage usage,
        String finishReason,
        List<LlmToolCall> toolCalls
) {
    public LlmResponse {
        toolCalls = toolCalls != null ? List.copyOf(toolCalls) : List.of();
    }

    public LlmResponse(String content, String model, LlmUsage usage) {
        this(content, model, usage, null);
    }

    public LlmResponse(String content, String model, LlmUsage usage, String finishReason) {
        this(content, model, usage, finishReason, List.of());
    }

    public boolean truncatedByLength() {
        return finishReason != null && finishReason.equalsIgnoreCase("length");
    }
}
