package com.ispf.ai;

import java.util.List;
import java.util.Map;

public record LlmRequest(
        String model,
        List<LlmMessage> messages,
        Integer maxTokens,
        Double temperature,
        Map<String, Object> providerOptions
) {
    public LlmRequest(String model, List<LlmMessage> messages, Integer maxTokens, Double temperature) {
        this(model, messages, maxTokens, temperature, Map.of());
    }
}
