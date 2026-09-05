package com.ispf.driver.secsgem;

import java.util.Locale;

/**
 * Point mapping for the HSMS/GEM-lab codec.
 * <p>
 * Accepted forms:
 * <ul>
 *   <li>{@code S1F1} / {@code areYouThere} — equipment online / MDLN+SOFTREV</li>
 *   <li>{@code status} / {@code S6F1} — lab status string/number</li>
 *   <li>{@code VID:100} / {@code 100} — equipment variable via S2F13/S2F14</li>
 * </ul>
 */
public record SecsGemPoint(Kind kind, long vid) {

    public enum Kind {
        S1F1,
        STATUS,
        VID
    }

    public static SecsGemPoint parse(String raw) {
        if (raw == null || raw.isBlank()) {
            throw new IllegalArgumentException("SECS/GEM point mapping is blank");
        }
        String trimmed = raw.trim();
        String upper = trimmed.toUpperCase(Locale.ROOT);
        if ("S1F1".equals(upper) || "AREYOUTHERE".equals(upper) || "ONLINE".equals(upper)) {
            return new SecsGemPoint(Kind.S1F1, -1);
        }
        if ("STATUS".equals(upper) || "S6F1".equals(upper) || "S6FX".equals(upper)) {
            return new SecsGemPoint(Kind.STATUS, -1);
        }
        if (upper.startsWith("VID:")) {
            return new SecsGemPoint(Kind.VID, parseVid(trimmed.substring(4).trim()));
        }
        if (upper.startsWith("V")) {
            return new SecsGemPoint(Kind.VID, parseVid(trimmed.substring(1).trim()));
        }
        return new SecsGemPoint(Kind.VID, parseVid(trimmed));
    }

    private static long parseVid(String token) {
        try {
            long vid = Long.parseLong(token.trim());
            if (vid < 0 || vid > 0xFFFFFFFFL) {
                throw new IllegalArgumentException("VID out of range: " + vid);
            }
            return vid;
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("Invalid SECS/GEM VID: " + token, e);
        }
    }
}
