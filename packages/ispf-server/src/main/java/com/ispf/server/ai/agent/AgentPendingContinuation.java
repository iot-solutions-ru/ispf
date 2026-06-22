package com.ispf.server.ai.agent;

import com.ispf.ai.LlmMessage;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * In-flight agent turn saved when the step budget is reached — resumed on continue.
 */
public final class AgentPendingContinuation {

    private final String userMessage;
    private final int stepsCompleted;
    private final List<Map<String, Object>> steps;
    private final List<LlmMessage> llmMessages;

    public AgentPendingContinuation(
            String userMessage,
            int stepsCompleted,
            List<Map<String, Object>> steps,
            List<LlmMessage> llmMessages
    ) {
        this.userMessage = userMessage != null ? userMessage : "";
        this.stepsCompleted = Math.max(0, stepsCompleted);
        this.steps = steps != null ? List.copyOf(steps) : List.of();
        this.llmMessages = llmMessages != null ? List.copyOf(llmMessages) : List.of();
    }

    public String userMessage() {
        return userMessage;
    }

    public int stepsCompleted() {
        return stepsCompleted;
    }

    public List<Map<String, Object>> steps() {
        return steps;
    }

    public List<LlmMessage> llmMessages() {
        return llmMessages;
    }

    Map<String, Object> toMap(ObjectMapper objectMapper) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("userMessage", userMessage);
        map.put("stepsCompleted", stepsCompleted);
        map.put("steps", steps);
        map.put("llmMessages", llmMessages.stream()
                .map(message -> Map.of("role", message.role(), "content", message.content()))
                .toList());
        return map;
    }

    @SuppressWarnings("unchecked")
    static Optional<AgentPendingContinuation> fromMap(ObjectMapper objectMapper, Object raw) {
        if (!(raw instanceof Map<?, ?> map) || map.isEmpty()) {
            return Optional.empty();
        }
        String userMessage = map.get("userMessage") != null ? String.valueOf(map.get("userMessage")) : "";
        int stepsCompleted = map.get("stepsCompleted") instanceof Number number
                ? number.intValue()
                : 0;
        List<Map<String, Object>> steps = readSteps(objectMapper, map.get("steps"));
        List<LlmMessage> messages = new ArrayList<>();
        Object llmRaw = map.get("llmMessages");
        if (llmRaw instanceof List<?> list) {
            for (Object item : list) {
                if (item instanceof Map<?, ?> row) {
                    messages.add(new LlmMessage(
                            String.valueOf(row.get("role")),
                            String.valueOf(row.get("content"))
                    ));
                }
            }
        }
        if (messages.isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(new AgentPendingContinuation(userMessage, stepsCompleted, steps, messages));
    }

    @SuppressWarnings("unchecked")
    private static List<Map<String, Object>> readSteps(ObjectMapper objectMapper, Object raw) {
        if (raw instanceof List<?> list) {
            List<Map<String, Object>> steps = new ArrayList<>();
            for (Object item : list) {
                if (item instanceof Map<?, ?> row) {
                    steps.add((Map<String, Object>) row);
                }
            }
            return steps;
        }
        if (raw instanceof String text && !text.isBlank()) {
            try {
                return objectMapper.readValue(text, new TypeReference<>() {});
            } catch (Exception ignored) {
                return List.of();
            }
        }
        return List.of();
    }
}
