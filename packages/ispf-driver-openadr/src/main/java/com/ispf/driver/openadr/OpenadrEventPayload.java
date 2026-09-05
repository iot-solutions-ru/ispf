package com.ispf.driver.openadr;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Minimal OpenADR 2.0b distribute-event subset parser (XML or JSON). Clean-room. */
final class OpenadrEventPayload {

    private static final Pattern EVENT_ID = Pattern.compile(
            "<(?:[\\w.-]+:)?eventID>([^<]*)</(?:[\\w.-]+:)?eventID>", Pattern.CASE_INSENSITIVE);
    private static final Pattern SIGNAL_NAME = Pattern.compile(
            "<(?:[\\w.-]+:)?signalName>([^<]*)</(?:[\\w.-]+:)?signalName>", Pattern.CASE_INSENSITIVE);
    private static final Pattern CURRENT_VALUE = Pattern.compile(
            "<(?:[\\w.-]+:)?currentValue>([^<]*)</(?:[\\w.-]+:)?currentValue>", Pattern.CASE_INSENSITIVE);
    private static final Pattern JSON_EVENT_ID = Pattern.compile(
            "\"eventID\"\\s*:\\s*\"([^\"]*)\"", Pattern.CASE_INSENSITIVE);
    private static final Pattern JSON_SIGNAL_NAME = Pattern.compile(
            "\"signalName\"\\s*:\\s*\"([^\"]*)\"", Pattern.CASE_INSENSITIVE);
    private static final Pattern JSON_CURRENT_VALUE = Pattern.compile(
            "\"currentValue\"\\s*:\\s*\"?([^,\"}\\s]+)\"?", Pattern.CASE_INSENSITIVE);

    final String eventId;
    final String signalName;
    final String signalLevel;
    final String raw;
    final boolean active;

    private OpenadrEventPayload(String eventId, String signalName, String signalLevel, String raw, boolean active) {
        this.eventId = eventId;
        this.signalName = signalName;
        this.signalLevel = signalLevel;
        this.raw = raw;
        this.active = active;
    }

    static OpenadrEventPayload parse(String body) {
        String raw = body == null ? "" : body.trim();
        if (raw.isEmpty()) {
            return new OpenadrEventPayload("", "", "", "", false);
        }
        if (raw.startsWith("{") || raw.startsWith("[")) {
            return parseJson(raw);
        }
        return parseXml(raw);
    }

    private static OpenadrEventPayload parseXml(String raw) {
        String eventId = first(EVENT_ID, raw);
        String signalName = first(SIGNAL_NAME, raw);
        String signalLevel = first(CURRENT_VALUE, raw);
        boolean active = !eventId.isEmpty();
        return new OpenadrEventPayload(eventId, signalName, signalLevel, raw, active);
    }

    private static OpenadrEventPayload parseJson(String raw) {
        String eventId = first(JSON_EVENT_ID, raw);
        String signalName = first(JSON_SIGNAL_NAME, raw);
        String signalLevel = first(JSON_CURRENT_VALUE, raw);
        boolean active = !eventId.isEmpty();
        return new OpenadrEventPayload(eventId, signalName, signalLevel, raw, active);
    }

    private static String first(Pattern pattern, String text) {
        Matcher matcher = pattern.matcher(text);
        return matcher.find() ? matcher.group(1).trim() : "";
    }

    String valueFor(OpenadrPoint point) {
        return switch (point.kind()) {
            case "eventId" -> eventId;
            case "signalLevel" -> signalLevel;
            case "signalName" -> signalName;
            case "active" -> String.valueOf(active);
            case "raw" -> raw;
            default -> "";
        };
    }
}
