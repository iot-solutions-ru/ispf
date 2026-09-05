package com.ispf.driver.mitsubishimelsec;

import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * MELSEC device point for data registers: {@code D100} or {@code D100:2}.
 */
public record MitsubishiMelsecPoint(int address, int wordCount) {

    private static final Pattern PATTERN = Pattern.compile("^D(\\d+)(?::(\\d+))?$", Pattern.CASE_INSENSITIVE);

    public static MitsubishiMelsecPoint parse(String raw) {
        if (raw == null || raw.isBlank()) {
            throw new IllegalArgumentException("Mitsubishi MELSEC point mapping is blank");
        }
        Matcher matcher = PATTERN.matcher(raw.trim());
        if (!matcher.matches()) {
            throw new IllegalArgumentException("Mitsubishi MELSEC point must be D<address> or D<address>:<words>");
        }
        int address = Integer.parseInt(matcher.group(1));
        int count = matcher.group(2) == null ? 1 : Integer.parseInt(matcher.group(2));
        if (count < 1) {
            throw new IllegalArgumentException("MELSEC word count must be >= 1");
        }
        return new MitsubishiMelsecPoint(address, count);
    }

    /** Binary device code for D* (data register) in MC/SLMP 3E binary. */
    public byte deviceCode() {
        return (byte) 0xA8;
    }

    @Override
    public String toString() {
        return "D" + address + (wordCount == 1 ? "" : ":" + wordCount);
    }

    String deviceLabel() {
        return "D";
    }
}
