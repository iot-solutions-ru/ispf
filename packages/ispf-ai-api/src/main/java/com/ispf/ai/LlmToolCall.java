package com.ispf.ai;

public record LlmToolCall(
        String id,
        String name,
        String argumentsJson
) {
}
