package com.ispf.server.platform.analytics.frames;

import com.fasterxml.jackson.annotation.JsonValue;

/**
 * Lightweight analytics time window type (BL-208).
 */
public enum EventFrameType {
    SHIFT("shift"),
    BATCH("batch"),
    DOWNTIME("downtime"),
    CUSTOM("custom");

    private final String externalName;

    EventFrameType(String externalName) {
        this.externalName = externalName;
    }

    @JsonValue
    public String externalName() {
        return externalName;
    }

    public static EventFrameType parse(String raw) {
        if (raw == null || raw.isBlank()) {
            throw new IllegalArgumentException("frameType is required");
        }
        String normalized = raw.trim().toLowerCase();
        for (EventFrameType type : values()) {
            if (type.externalName.equals(normalized) || type.name().equalsIgnoreCase(normalized)) {
                return type;
            }
        }
        throw new IllegalArgumentException("Unknown frame type: " + raw);
    }
}
