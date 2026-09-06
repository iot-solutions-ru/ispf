package com.ispf.driver.lonworks.codec;

import java.nio.charset.StandardCharsets;
import java.util.Locale;

/**
 * LonWorks LonTalk-IP / LON-over-TCP gateway lab codec — ASCII line request/response.
 * <p>
 * Not a native twisted-pair LonTalk master and not an Echelon/Adesto stack.
 * Clean-room Apache-2.0, JDK only.
 * <p>
 * Lines: {@code GET <nv>}, {@code SET <nv> <float>}; responses {@code OK <float>} or {@code OK}.
 */
public final class LonworksLabCodec {

    private LonworksLabCodec() {
    }

    public static String encodeGet(String nvToken) {
        return "GET " + nvToken + "\n";
    }

    public static String encodeSet(String nvToken, float value) {
        return "SET " + nvToken + " " + Float.toString(value) + "\n";
    }

    public static byte[] encodeGetBytes(String nvToken) {
        return encodeGet(nvToken).getBytes(StandardCharsets.US_ASCII);
    }

    public static byte[] encodeSetBytes(String nvToken, float value) {
        return encodeSet(nvToken, value).getBytes(StandardCharsets.US_ASCII);
    }

    public static float parseOkValue(String responseLine) {
        if (responseLine == null || responseLine.isBlank()) {
            throw new IllegalArgumentException("Empty LonWorks gateway response");
        }
        String line = responseLine.trim();
        String upper = line.toUpperCase(Locale.ROOT);
        if (upper.startsWith("ERR")) {
            throw new IllegalArgumentException("LonWorks gateway error: " + line);
        }
        if (!upper.startsWith("OK")) {
            throw new IllegalArgumentException("Unexpected LonWorks gateway response: " + line);
        }
        String rest = line.length() > 2 ? line.substring(2).trim() : "";
        if (rest.isEmpty()) {
            throw new IllegalArgumentException("LonWorks gateway OK without value");
        }
        return Float.parseFloat(rest);
    }

    public static void parseOkAck(String responseLine) {
        if (responseLine == null || responseLine.isBlank()) {
            throw new IllegalArgumentException("Empty LonWorks gateway response");
        }
        String line = responseLine.trim();
        String upper = line.toUpperCase(Locale.ROOT);
        if (upper.startsWith("ERR")) {
            throw new IllegalArgumentException("LonWorks gateway error: " + line);
        }
        if (!upper.startsWith("OK")) {
            throw new IllegalArgumentException("Unexpected LonWorks gateway response: " + line);
        }
    }

    public static Request parseRequest(String line) {
        if (line == null || line.isBlank()) {
            throw new IllegalArgumentException("Empty LonWorks gateway request");
        }
        String trimmed = line.trim();
        String[] parts = trimmed.split("\\s+");
        if (parts.length < 2) {
            throw new IllegalArgumentException("Malformed LonWorks gateway request: " + line);
        }
        String verb = parts[0].toUpperCase(Locale.ROOT);
        if ("GET".equals(verb)) {
            return new GetRequest(parts[1]);
        }
        if ("SET".equals(verb)) {
            if (parts.length < 3) {
                throw new IllegalArgumentException("SET requires value: " + line);
            }
            return new SetRequest(parts[1], Float.parseFloat(parts[2]));
        }
        throw new IllegalArgumentException("Unsupported LonWorks gateway verb: " + verb);
    }

    public sealed interface Request permits GetRequest, SetRequest {
    }

    public record GetRequest(String nvToken) implements Request {
    }

    public record SetRequest(String nvToken, float value) implements Request {
    }
}
