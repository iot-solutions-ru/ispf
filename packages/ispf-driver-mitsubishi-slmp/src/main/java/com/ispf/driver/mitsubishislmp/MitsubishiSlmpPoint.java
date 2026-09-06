package com.ispf.driver.mitsubishislmp;

import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Point mapping for SLMP device memory: {@code D100}, {@code D:100}, or {@code D:100:1}.
 */
public record MitsubishiSlmpPoint(String deviceCode, int address, int count) {

    private static final Pattern COMPACT = Pattern.compile("^([A-Za-z]+)(\\d+)$");

    public static MitsubishiSlmpPoint parse(String raw) {
        if (raw == null || raw.isBlank()) {
            throw new IllegalArgumentException("Mitsubishi SLMP point mapping is blank");
        }
        String trimmed = raw.trim();
        if (trimmed.contains(":")) {
            String[] parts = trimmed.split(":");
            if (parts.length < 2 || parts.length > 3) {
                throw new IllegalArgumentException("Mitsubishi SLMP point must be D:address[:count]");
            }
            String code = parts[0].trim().toUpperCase(Locale.ROOT);
            int address = Integer.parseInt(parts[1].trim());
            int count = parts.length == 3 ? Integer.parseInt(parts[2].trim()) : 1;
            return new MitsubishiSlmpPoint(requireSupported(code), address, requirePositive(count));
        }
        Matcher matcher = COMPACT.matcher(trimmed);
        if (!matcher.matches()) {
            throw new IllegalArgumentException("Mitsubishi SLMP point must be like D100 or D:100:1");
        }
        return new MitsubishiSlmpPoint(
                requireSupported(matcher.group(1).toUpperCase(Locale.ROOT)),
                Integer.parseInt(matcher.group(2)),
                1
        );
    }

    private static String requireSupported(String code) {
        if (!"D".equals(code)) {
            throw new IllegalArgumentException("Mitsubishi SLMP v0.1 supports D registers only, got: " + code);
        }
        return code;
    }

    private static int requirePositive(int count) {
        if (count < 1) {
            throw new IllegalArgumentException("SLMP point count must be >= 1");
        }
        return count;
    }

    /** Binary device code for D* data registers. */
    public byte binaryDeviceCode() {
        return (byte) 0xA8;
    }
}
