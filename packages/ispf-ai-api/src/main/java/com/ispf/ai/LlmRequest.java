package com.ispf.ai;

import java.util.List;
import java.util.Map;

public record LlmRequest(
        String model,
        List<LlmMessage> messages,
        Integer maxTokens,
        Double temperature,
        Map<String, Object> providerOptions,
        List<LlmToolSpec> tools,
        String toolChoice
) {
    public LlmRequest {
        messages = messages != null ? List.copyOf(messages) : List.of();
        providerOptions = providerOptions != null ? Map.copyOf(providerOptions) : Map.of();
        tools = tools != null ? List.copyOf(tools) : List.of();
    }

    public LlmRequest(String model, List<LlmMessage> messages, Integer maxTokens, Double temperature) {
        this(model, messages, maxTokens, temperature, Map.of(), List.of(), null);
    }

    public LlmRequest(
            String model,
            List<LlmMessage> messages,
            Integer maxTokens,
            Double temperature,
            Map<String, Object> providerOptions
    ) {
        this(model, messages, maxTokens, temperature, providerOptions, List.of(), null);
    }
}
