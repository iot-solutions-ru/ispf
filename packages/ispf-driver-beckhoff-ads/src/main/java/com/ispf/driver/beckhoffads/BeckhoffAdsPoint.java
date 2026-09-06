package com.ispf.driver.beckhoffads;

import java.util.Locale;

/**
 * ADS point address: {@code indexGroup:indexOffset:TYPE} (decimal or {@code 0x} hex).
 * Supported types: INT (2), DINT (4), REAL (4), STRING / STRING:n (null-terminated bytes).
 */
public record BeckhoffAdsPoint(long indexGroup, long indexOffset, AdsValueType type, int byteLength) {

    public enum AdsValueType {
        INT(2),
        DINT(4),
        REAL(4),
        STRING(-1);

        private final int fixedBytes;

        AdsValueType(int fixedBytes) {
            this.fixedBytes = fixedBytes;
        }

        int resolveBytes(int stringCapacity) {
            if (this == STRING) {
                return stringCapacity;
            }
            return fixedBytes;
        }
    }

    private static final int DEFAULT_STRING_BYTES = 81;

    public static BeckhoffAdsPoint parse(String raw) {
        if (raw == null || raw.isBlank()) {
            throw new IllegalArgumentException("Beckhoff ADS point mapping is blank");
        }
        String[] parts = raw.trim().split(":");
        if (parts.length < 3 || parts.length > 4) {
            throw new IllegalArgumentException(
                    "Beckhoff ADS point must be indexGroup:indexOffset:TYPE or indexGroup:indexOffset:STRING:n"
            );
        }
        long indexGroup = parseUnsigned(parts[0].trim());
        long indexOffset = parseUnsigned(parts[1].trim());
        String typeToken = parts[2].trim().toUpperCase(Locale.ROOT);
        AdsValueType type;
        int byteLength;
        if (typeToken.startsWith("STRING")) {
            type = AdsValueType.STRING;
            if (parts.length == 4) {
                byteLength = Integer.parseInt(parts[3].trim());
            } else if (typeToken.contains("(") && typeToken.endsWith(")")) {
                int open = typeToken.indexOf('(');
                byteLength = Integer.parseInt(typeToken.substring(open + 1, typeToken.length() - 1)) + 1;
            } else {
                byteLength = DEFAULT_STRING_BYTES;
            }
            if (byteLength < 1) {
                throw new IllegalArgumentException("STRING byte length must be >= 1");
            }
        } else {
            type = AdsValueType.valueOf(typeToken);
            if (parts.length == 4) {
                throw new IllegalArgumentException("Extra segment only allowed for STRING capacity");
            }
            byteLength = type.resolveBytes(DEFAULT_STRING_BYTES);
        }
        return new BeckhoffAdsPoint(indexGroup, indexOffset, type, byteLength);
    }

    private static long parseUnsigned(String token) {
        String t = token.trim();
        if (t.regionMatches(true, 0, "0x", 0, 2)) {
            return Long.parseUnsignedLong(t.substring(2), 16);
        }
        return Long.parseUnsignedLong(t);
    }
}
