package com.ispf.driver.lsxgt;

import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Point mapping for XGT-lab device memory.
 * <p>
 * Accepted forms: {@code %DW100}, {@code DW100}, {@code %DW100:2}, {@code %MW10}, {@code MW10},
 * {@code %MX0}, {@code MX0}. Optional {@code :count} applies to word devices (DW/MW).
 * Bit device MX is always count 1 (0/1 word value in the lab framing).
 */
public record LsXgtPoint(DeviceType deviceType, int address, int count) {

    private static final Pattern COMPACT = Pattern.compile("^%?([DdMm][WwXx])(\\d+)(?::(\\d+))?$");

    public enum DeviceType {
        DW((byte) 0x01),
        MW((byte) 0x02),
        MX((byte) 0x03);

        private final byte code;

        DeviceType(byte code) {
            this.code = code;
        }

        public byte code() {
            return code;
        }

        static DeviceType fromToken(String token) {
            return switch (token.toUpperCase(Locale.ROOT)) {
                case "DW" -> DW;
                case "MW" -> MW;
                case "MX" -> MX;
                default -> throw new IllegalArgumentException("LS XGT lab supports DW/MW/MX only, got: " + token);
            };
        }
    }

    public static LsXgtPoint parse(String raw) {
        if (raw == null || raw.isBlank()) {
            throw new IllegalArgumentException("LS XGT point mapping is blank");
        }
        String trimmed = raw.trim();
        Matcher matcher = COMPACT.matcher(trimmed);
        if (!matcher.matches()) {
            throw new IllegalArgumentException(
                    "LS XGT point must be like %DW100, DW100, %MW10, or %MX0[:count for DW/MW]");
        }
        DeviceType type = DeviceType.fromToken(matcher.group(1));
        int address = Integer.parseInt(matcher.group(2));
        int count = matcher.group(3) != null ? Integer.parseInt(matcher.group(3)) : 1;
        if (type == DeviceType.MX && count != 1) {
            throw new IllegalArgumentException("LS XGT lab MX points are single-bit (count must be 1)");
        }
        return new LsXgtPoint(type, address, requirePositive(count));
    }

    private static int requirePositive(int count) {
        if (count < 1) {
            throw new IllegalArgumentException("LS XGT point count must be >= 1");
        }
        return count;
    }
}
