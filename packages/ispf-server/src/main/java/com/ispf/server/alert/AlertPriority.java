package com.ispf.server.alert;

/**
 * Alert rule priority for operator sorting and escalation (BL-87).
 */
public enum AlertPriority {
    CRITICAL(4),
    HIGH(3),
    MEDIUM(2),
    LOW(1);

    private final int rank;

    AlertPriority(int rank) {
        this.rank = rank;
    }

    public int rank() {
        return rank;
    }

    public static AlertPriority parse(String raw) {
        if (raw == null || raw.isBlank()) {
            return MEDIUM;
        }
        try {
            return AlertPriority.valueOf(raw.trim().toUpperCase());
        } catch (IllegalArgumentException ex) {
            return MEDIUM;
        }
    }
}
