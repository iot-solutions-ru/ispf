package com.ispf.ai;

import java.util.List;

public record LlmMessage(
        String role,
        String content,
        List<LlmContentPart> parts,
        List<LlmToolCall> toolCalls,
        String toolCallId
) {
    public LlmMessage {
        parts = parts != null ? List.copyOf(parts) : null;
        toolCalls = toolCalls != null ? List.copyOf(toolCalls) : List.of();
    }

    public LlmMessage(String role, String content) {
        this(role, content, null, List.of(), null);
    }

    public LlmMessage(String role, String content, List<LlmContentPart> parts) {
        this(role, content, parts, List.of(), null);
    }

    public boolean hasMultimodalParts() {
        return parts != null && !parts.isEmpty();
    }

    public boolean hasToolCalls() {
        return toolCalls != null && !toolCalls.isEmpty();
    }
}
