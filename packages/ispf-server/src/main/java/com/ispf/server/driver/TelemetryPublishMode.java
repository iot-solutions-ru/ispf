package com.ispf.server.driver;

/**
 * Controls whether coalesced driver telemetry enters the automation lane (alerts, workflows).
 */
public enum TelemetryPublishMode {
    /** Historian + WebSocket + alert/workflow evaluation (default). */
    FULL,
    /** RAM + telemetry lane only; no alert CEL on each coalesced update. */
    TELEMETRY_ONLY,
    /** RAM + async event journal only (one driver ingress update → one journal event). */
    EVENT_JOURNAL_ONLY;

    public static TelemetryPublishMode parse(String raw) {
        if (raw == null || raw.isBlank()) {
            return FULL;
        }
        return switch (raw.trim().toUpperCase()) {
            case "TELEMETRY_ONLY", "TELEMETRY", "HISTORY_ONLY" -> TELEMETRY_ONLY;
            case "EVENT_JOURNAL_ONLY", "EVENT_JOURNAL", "EVENTS_ONLY" -> EVENT_JOURNAL_ONLY;
            default -> FULL;
        };
    }

    public static void validateOverride(String raw) {
        if (raw == null || raw.isBlank() || "INHERIT".equalsIgnoreCase(raw.trim())) {
            return;
        }
        String normalized = raw.trim().toUpperCase();
        if (normalized.equals("FULL")
                || normalized.equals("TELEMETRY_ONLY")
                || normalized.equals("EVENT_JOURNAL_ONLY")) {
            return;
        }
        throw new IllegalArgumentException("Unknown telemetryPublishMode: " + raw);
    }

    public boolean automationEligible() {
        return this == FULL;
    }
}
