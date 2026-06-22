package com.ispf.server.ai.agent;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public final class AgentTurn {

    private final String turnId;
    private final String userMessage;
    private final String assistantSummary;
    private final String status;
    private final List<Map<String, Object>> steps;
    private final Map<String, Object> result;
    private final Instant createdAt;

    public AgentTurn(
            String turnId,
            String userMessage,
            String assistantSummary,
            String status,
            List<Map<String, Object>> steps,
            Map<String, Object> result,
            Instant createdAt
    ) {
        this.turnId = turnId;
        this.userMessage = userMessage;
        this.assistantSummary = assistantSummary;
        this.status = status;
        this.steps = steps != null ? List.copyOf(steps) : List.of();
        this.result = result != null ? Map.copyOf(result) : Map.of();
        this.createdAt = createdAt != null ? createdAt : Instant.now();
    }

    public static AgentTurn create(
            String userMessage,
            String assistantSummary,
            String status,
            List<Map<String, Object>> steps,
            Map<String, Object> result
    ) {
        return new AgentTurn(
                UUID.randomUUID().toString(),
                userMessage,
                assistantSummary,
                status,
                steps,
                result,
                Instant.now()
        );
    }

    public String turnId() {
        return turnId;
    }

    public String userMessage() {
        return userMessage;
    }

    public String assistantSummary() {
        return assistantSummary;
    }

    public String status() {
        return status;
    }

    public List<Map<String, Object>> steps() {
        return steps;
    }

    public Map<String, Object> result() {
        return result;
    }

    public Instant createdAt() {
        return createdAt;
    }

    public Map<String, Object> toMap() {
        return Map.of(
                "turnId", turnId,
                "userMessage", userMessage,
                "assistantSummary", assistantSummary,
                "status", status,
                "steps", steps,
                "result", result,
                "createdAt", createdAt.toString()
        );
    }
}
