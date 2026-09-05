package com.ispf.driver.deltadvp;

import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Point mapping for Delta DVP Modbus holding registers.
 * <p>
 * Accepted forms: {@code HR:100}, {@code HR:100:2}, {@code 100}, {@code D100}, {@code D:100},
 * {@code D:100:2}. Lab mapping is 1:1 — the numeric suffix is the Modbus holding-register
 * address. {@code D100} and {@code HR:100} both address register 100. This does not encode
 * vendor-specific D-to-Modbus base offsets used by some Delta AS/DVP series (e.g. 0x1000).
 */
public record DeltaDvpPoint(int address, int count) {

    private static final Pattern COMPACT_D = Pattern.compile("^[Dd](\\d+)$");

    public static DeltaDvpPoint parse(String raw) {
        if (raw == null || raw.isBlank()) {
            throw new IllegalArgumentException("Delta DVP point mapping is blank");
        }
        String trimmed = raw.trim();
        if (trimmed.contains(":")) {
            String[] parts = trimmed.split(":");
            if (parts.length < 2 || parts.length > 3) {
                throw new IllegalArgumentException("Delta DVP point must be HR:address[:count] or D:address[:count]");
            }
            String area = parts[0].trim().toUpperCase(Locale.ROOT);
            if (!"HR".equals(area) && !"HOLDING".equals(area) && !"D".equals(area)) {
                throw new IllegalArgumentException("Delta DVP v0.1 supports HR/D holding registers only, got: " + area);
            }
            int address = Integer.parseInt(parts[1].trim());
            int count = parts.length == 3 ? Integer.parseInt(parts[2].trim()) : 1;
            return new DeltaDvpPoint(address, requirePositive(count));
        }
        Matcher dMatcher = COMPACT_D.matcher(trimmed);
        if (dMatcher.matches()) {
            return new DeltaDvpPoint(Integer.parseInt(dMatcher.group(1)), 1);
        }
        return new DeltaDvpPoint(Integer.parseInt(trimmed), 1);
    }

    private static int requirePositive(int count) {
        if (count < 1) {
            throw new IllegalArgumentException("Delta DVP point count must be >= 1");
        }
        return count;
    }
}
