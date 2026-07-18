package com.ispf.server.ai.agent;

public enum AgentProfile {
    ADMIN,
    OPERATOR;

    public static AgentProfile fromString(String raw) {
        if (raw == null || raw.isBlank()) {
            return ADMIN;
        }
        return "operator".equalsIgnoreCase(raw.trim()) ? OPERATOR : ADMIN;
    }

    public String storageValue() {
        return name().toLowerCase();
    }
}
