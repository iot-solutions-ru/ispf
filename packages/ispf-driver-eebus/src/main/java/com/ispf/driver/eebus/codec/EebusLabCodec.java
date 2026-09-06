package com.ispf.driver.eebus.codec;

import java.nio.charset.StandardCharsets;
import java.util.Locale;

/**
 * EEBus SHIP/SPINE-lite over TCP lab codec — ASCII line request/response.
 * <p>
 * Not a full EEBus SHIP TLS stack and not an official EEBus SDK.
 * Clean-room Apache-2.0, JDK only.
 * <p>
 * Lines: {@code GET <token>}, {@code SET <token> <float>}; responses {@code OK <float>} or {@code OK}.
 * Tokens: {@code power}, {@code setpoint}, {@code entity:ElectricalConnection:power}.
 * Also accepts minimal SPINE-like JSON:
 * {@code {"op":"read","entity":"ElectricalConnection","path":"PowerConsumption"}}.
 */
public final class EebusLabCodec {

    private EebusLabCodec() {
    }

    public static String encodeGet(String token) {
        return "GET " + token + "\n";
    }

    public static String encodeSet(String token, float value) {
        return "SET " + token + " " + Float.toString(value) + "\n";
    }

    public static byte[] encodeGetBytes(String token) {
        return encodeGet(token).getBytes(StandardCharsets.US_ASCII);
    }

    public static byte[] encodeSetBytes(String token, float value) {
        return encodeSet(token, value).getBytes(StandardCharsets.US_ASCII);
    }

    public static float parseOkValue(String responseLine) {
        if (responseLine == null || responseLine.isBlank()) {
            throw new IllegalArgumentException("Empty EEBus lab response");
        }
        String line = responseLine.trim();
        String upper = line.toUpperCase(Locale.ROOT);
        if (upper.startsWith("ERR")) {
            throw new IllegalArgumentException("EEBus lab error: " + line);
        }
        if (!upper.startsWith("OK")) {
            throw new IllegalArgumentException("Unexpected EEBus lab response: " + line);
        }
        String rest = line.length() > 2 ? line.substring(2).trim() : "";
        if (rest.isEmpty()) {
            throw new IllegalArgumentException("EEBus lab OK without value");
        }
        return Float.parseFloat(rest);
    }

    public static void parseOkAck(String responseLine) {
        if (responseLine == null || responseLine.isBlank()) {
            throw new IllegalArgumentException("Empty EEBus lab response");
        }
        String line = responseLine.trim();
        String upper = line.toUpperCase(Locale.ROOT);
        if (upper.startsWith("ERR")) {
            throw new IllegalArgumentException("EEBus lab error: " + line);
        }
        if (!upper.startsWith("OK")) {
            throw new IllegalArgumentException("Unexpected EEBus lab response: " + line);
        }
    }

    public static Request parseRequest(String line) {
        if (line == null || line.isBlank()) {
            throw new IllegalArgumentException("Empty EEBus lab request");
        }
        String trimmed = line.trim();
        if (trimmed.startsWith("{")) {
            return parseJsonRequest(trimmed);
        }
        String[] parts = trimmed.split("\\s+");
        if (parts.length < 2) {
            throw new IllegalArgumentException("Malformed EEBus lab request: " + line);
        }
        String verb = parts[0].toUpperCase(Locale.ROOT);
        if ("GET".equals(verb)) {
            return new GetRequest(normalizeToken(parts[1]));
        }
        if ("SET".equals(verb)) {
            if (parts.length < 3) {
                throw new IllegalArgumentException("SET requires value: " + line);
            }
            return new SetRequest(normalizeToken(parts[1]), Float.parseFloat(parts[2]));
        }
        throw new IllegalArgumentException("Unsupported EEBus lab verb: " + verb);
    }

    private static Request parseJsonRequest(String json) {
        String lower = json.toLowerCase(Locale.ROOT);
        String op = extractJsonString(lower, "op");
        if (op == null) {
            throw new IllegalArgumentException("EEBus lab JSON missing op: " + json);
        }
        String entity = extractJsonString(json, "entity");
        String path = extractJsonString(json, "path");
        if (path == null || path.isBlank()) {
            throw new IllegalArgumentException("EEBus lab JSON missing path: " + json);
        }
        String token = toToken(entity, path);
        if ("read".equals(op) || "get".equals(op)) {
            return new GetRequest(token);
        }
        if ("write".equals(op) || "set".equals(op)) {
            Float value = extractJsonNumber(json, "value");
            if (value == null) {
                throw new IllegalArgumentException("EEBus lab JSON write missing value: " + json);
            }
            return new SetRequest(token, value);
        }
        throw new IllegalArgumentException("Unsupported EEBus lab JSON op: " + op);
    }

    private static String toToken(String entity, String path) {
        String normalizedPath = normalizePath(path);
        if (entity == null || entity.isBlank()
                || "ElectricalConnection".equalsIgnoreCase(entity)) {
            if ("power".equals(normalizedPath) || "setpoint".equals(normalizedPath)) {
                return normalizedPath;
            }
        }
        String ent = entity == null || entity.isBlank() ? "ElectricalConnection" : entity.trim();
        return "entity:" + ent + ":" + normalizedPath;
    }

    private static String normalizePath(String path) {
        String p = path.trim();
        if ("PowerConsumption".equalsIgnoreCase(p) || "power".equalsIgnoreCase(p)) {
            return "power";
        }
        if ("Setpoint".equalsIgnoreCase(p) || "setpoint".equalsIgnoreCase(p)) {
            return "setpoint";
        }
        return p;
    }

    private static String normalizeToken(String token) {
        if (token == null || token.isBlank()) {
            throw new IllegalArgumentException("Blank EEBus lab token");
        }
        String t = token.trim();
        if (t.toLowerCase(Locale.ROOT).startsWith("entity:")) {
            return t;
        }
        return normalizePath(t);
    }

    private static String extractJsonString(String json, String field) {
        String needle = "\"" + field.toLowerCase(Locale.ROOT) + "\"";
        String lower = json.toLowerCase(Locale.ROOT);
        int idx = lower.indexOf(needle);
        if (idx < 0) {
            return null;
        }
        int colon = json.indexOf(':', idx + needle.length());
        if (colon < 0) {
            return null;
        }
        int startQuote = json.indexOf('"', colon + 1);
        if (startQuote < 0) {
            return null;
        }
        int endQuote = json.indexOf('"', startQuote + 1);
        if (endQuote < 0) {
            return null;
        }
        return json.substring(startQuote + 1, endQuote);
    }

    private static Float extractJsonNumber(String json, String field) {
        String needle = "\"" + field + "\"";
        int idx = json.toLowerCase(Locale.ROOT).indexOf(needle.toLowerCase(Locale.ROOT));
        if (idx < 0) {
            return null;
        }
        int colon = json.indexOf(':', idx + needle.length());
        if (colon < 0) {
            return null;
        }
        int i = colon + 1;
        while (i < json.length() && Character.isWhitespace(json.charAt(i))) {
            i++;
        }
        int j = i;
        while (j < json.length()) {
            char c = json.charAt(j);
            if ((c >= '0' && c <= '9') || c == '.' || c == '-' || c == '+' || c == 'e' || c == 'E') {
                j++;
            } else {
                break;
            }
        }
        if (j == i) {
            return null;
        }
        return Float.parseFloat(json.substring(i, j));
    }

    public sealed interface Request permits GetRequest, SetRequest {
    }

    public record GetRequest(String token) implements Request {
    }

    public record SetRequest(String token, float value) implements Request {
    }
}
