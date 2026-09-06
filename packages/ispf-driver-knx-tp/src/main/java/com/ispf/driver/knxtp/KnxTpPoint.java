package com.ispf.driver.knxtp;

import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * KNX group-address point mapping: {@code 1/2/3} (three-level) or {@code 1/2} (two-level).
 */
public record KnxTpPoint(int main, int middle, int sub, boolean threeLevel) {

    private static final Pattern THREE = Pattern.compile("^(\\d+)/(\\d+)/(\\d+)$");
    private static final Pattern TWO = Pattern.compile("^(\\d+)/(\\d+)$");

    public static KnxTpPoint parse(String raw) {
        if (raw == null || raw.isBlank()) {
            throw new IllegalArgumentException("KNX TP point mapping is blank");
        }
        String trimmed = raw.trim();
        Matcher three = THREE.matcher(trimmed);
        if (three.matches()) {
            return new KnxTpPoint(
                    parseRange(three.group(1), 0, 31, "main"),
                    parseRange(three.group(2), 0, 7, "middle"),
                    parseRange(three.group(3), 0, 255, "sub"),
                    true
            );
        }
        Matcher two = TWO.matcher(trimmed);
        if (two.matches()) {
            return new KnxTpPoint(
                    parseRange(two.group(1), 0, 31, "main"),
                    0,
                    parseRange(two.group(2), 0, 2047, "sub"),
                    false
            );
        }
        throw new IllegalArgumentException("KNX TP point must be like 1/2/3 or 1/2, got: " + trimmed);
    }

    public int groupAddress() {
        if (threeLevel) {
            return ((main & 0x1F) << 11) | ((middle & 0x07) << 8) | (sub & 0xFF);
        }
        return ((main & 0x1F) << 11) | (sub & 0x7FF);
    }

    public String addressText() {
        if (threeLevel) {
            return main + "/" + middle + "/" + sub;
        }
        return main + "/" + sub;
    }

    @Override
    public String toString() {
        return addressText().toLowerCase(Locale.ROOT);
    }

    private static int parseRange(String text, int min, int max, String label) {
        int value = Integer.parseInt(text);
        if (value < min || value > max) {
            throw new IllegalArgumentException("KNX " + label + " out of range " + min + ".." + max + ": " + value);
        }
        return value;
    }
}
