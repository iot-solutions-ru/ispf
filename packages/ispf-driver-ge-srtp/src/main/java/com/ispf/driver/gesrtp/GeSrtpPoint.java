package com.ispf.driver.gesrtp;

import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * GE Fanuc / Emerson memory point for the SRTP-lab subset.
 * <p>
 * Accepted forms: {@code %R100}, {@code R100}, {@code %AI1}, {@code %AQ2},
 * {@code %I10}, {@code %Q5}, optional element count {@code %R100:2}.
 */
public record GeSrtpPoint(GeSrtpMemoryType memoryType, int address, int count) {

    private static final Pattern PATTERN = Pattern.compile(
            "^%?(R|AI|AQ|I|Q)(\\d+)(?::(\\d+))?$",
            Pattern.CASE_INSENSITIVE
    );

    public static GeSrtpPoint parse(String raw) {
        if (raw == null || raw.isBlank()) {
            throw new IllegalArgumentException("GE SRTP-lab point mapping is blank");
        }
        Matcher matcher = PATTERN.matcher(raw.trim());
        if (!matcher.matches()) {
            throw new IllegalArgumentException(
                    "GE SRTP-lab point must be %R/%AI/%AQ/%I/%Q or R/AI/AQ/I/Q + address"
                            + " (optional :count), e.g. %R100, R100, %AI1"
            );
        }
        GeSrtpMemoryType type = GeSrtpMemoryType.fromToken(matcher.group(1));
        int address = Integer.parseInt(matcher.group(2));
        int count = matcher.group(3) == null ? 1 : Integer.parseInt(matcher.group(3));
        if (address < 0) {
            throw new IllegalArgumentException("GE SRTP-lab address must be >= 0");
        }
        if (count < 1) {
            throw new IllegalArgumentException("GE SRTP-lab count must be >= 1");
        }
        return new GeSrtpPoint(type, address, count);
    }

    public String deviceLabel() {
        return memoryType.token();
    }

    @Override
    public String toString() {
        String base = "%" + memoryType.token() + address;
        return count == 1 ? base : base + ":" + count;
    }

    /**
     * Emerson/GE Fanuc memory classes used by the SRTP-lab subset.
     * Type codes are conventional public values used by Series 90 / Rx3i tooling literature.
     */
    public enum GeSrtpMemoryType {
        R("R", (byte) 0x08),
        AI("AI", (byte) 0x0A),
        AQ("AQ", (byte) 0x0C),
        I("I", (byte) 0x10),
        Q("Q", (byte) 0x12);

        private final String token;
        private final byte typeCode;

        GeSrtpMemoryType(String token, byte typeCode) {
            this.token = token;
            this.typeCode = typeCode;
        }

        public String token() {
            return token;
        }

        public byte typeCode() {
            return typeCode;
        }

        static GeSrtpMemoryType fromToken(String token) {
            String normalized = token.toUpperCase(Locale.ROOT);
            for (GeSrtpMemoryType type : values()) {
                if (type.token.equals(normalized)) {
                    return type;
                }
            }
            throw new IllegalArgumentException("Unsupported GE SRTP-lab memory type: " + token);
        }

        static GeSrtpMemoryType fromTypeCode(byte code) {
            for (GeSrtpMemoryType type : values()) {
                if (type.typeCode == code) {
                    return type;
                }
            }
            throw new IllegalArgumentException("Unknown GE SRTP-lab type code 0x" + Integer.toHexString(code & 0xFF));
        }
    }
}
