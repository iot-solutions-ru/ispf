package com.ispf.ai;

import java.util.Map;

public record LlmToolSpec(
        String name,
        String description,
        Map<String, Object> parameters
) {
    public LlmToolSpec {
        parameters = parameters != null ? Map.copyOf(parameters) : Map.of();
    }
}
