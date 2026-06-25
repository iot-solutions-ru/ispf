package com.ispf.server.driver;

/**
 * Controls whether coalesced driver telemetry enters the automation lane (alerts, workflows).
 */
public enum TelemetryPublishMode {
    /** Historian + WebSocket + alert/workflow evaluation (default). */
    FULL,
    /** RAM + telemetry lane only; no alert CEL on each coalesced update. */
    TELEMETRY_ONLY;

    public static TelemetryPublishMode parse(String raw) {
        if (raw == null || raw.isBlank()) {
            return FULL;
        }
        return switch (raw.trim().toUpperCase()) {
            case "TELEMETRY_ONLY", "TELEMETRY", "HISTORY_ONLY" -> TELEMETRY_ONLY;
            default -> FULL;
        };
    }

    public boolean automationEligible() {
        return this == FULL;
    }
}
