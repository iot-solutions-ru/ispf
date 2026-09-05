package com.ispf.driver.openadr;

/** OpenADR point: eventId | signalLevel | signalName | active | raw */
public record OpenadrPoint(String kind) {

    public static OpenadrPoint parse(String raw) {
        if (raw == null || raw.isBlank()) {
            throw new IllegalArgumentException("OpenADR point mapping is blank");
        }
        String kind = raw.trim().toLowerCase();
        return switch (kind) {
            case "eventid", "event_id", "ei:eventid" -> new OpenadrPoint("eventId");
            case "signallevel", "signal_level", "currentvalue", "current_value", "ei:currentvalue" ->
                    new OpenadrPoint("signalLevel");
            case "signalname", "signal_name", "ei:signalname" -> new OpenadrPoint("signalName");
            case "active", "has_event" -> new OpenadrPoint("active");
            case "raw", "payload" -> new OpenadrPoint("raw");
            default -> throw new IllegalArgumentException(
                    "Unknown OpenADR point: " + raw + " (use eventId|signalLevel|signalName|active|raw)");
        };
    }
}
