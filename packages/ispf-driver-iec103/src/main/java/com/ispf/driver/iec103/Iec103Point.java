package com.ispf.driver.iec103;

import java.util.Locale;

/**
 * Point mapping for the IEC103-lab codec.
 * <p>
 * Accepted forms:
 * <ul>
 *   <li>{@code FUN:INF} — e.g. {@code 1:40} (defaults to lab measured float ASDU 40)</li>
 *   <li>{@code ASDU:FUN:INF} — e.g. {@code 1:2:16}, {@code 40:1:40}, {@code 9:1:1}</li>
 *   <li>{@code TYPE:FUN:INF} — {@code STATUS}/{@code ASDU1}, {@code MEAS}/{@code ASDU9}/{@code ASDU40}</li>
 *   <li>{@code ASDUid:IOA} — e.g. {@code 40:296} where IOA packs {@code (FUN<<8)|INF}</li>
 * </ul>
 */
public record Iec103Point(int fun, int inf, Kind kind) {

    public enum Kind {
        STATUS,
        MEASURED_FLOAT,
        MEASURANDS_II
    }

    public int packedIoa() {
        return ((fun & 0xFF) << 8) | (inf & 0xFF);
    }

    public static Iec103Point parse(String raw) {
        if (raw == null || raw.isBlank()) {
            throw new IllegalArgumentException("IEC103 point mapping is blank");
        }
        String trimmed = raw.trim();
        String[] parts = trimmed.split(":");
        if (parts.length == 2) {
            String left = parts[0].trim();
            String right = parts[1].trim();
            // Prefer FUN:INF for two byte-sized numbers (e.g. 1:40).
            // ASDUid:IOA when left is a named type or packed IOA > 255 (e.g. ASDU40:296).
            if (isNamedAsduToken(left) && isNumeric(right)) {
                return fromAsduIoa(parseAsduNumber(left), parseByteOrIoa(right, "IOA"));
            }
            if (isNumeric(left) && isNumeric(right)) {
                int maybeAsdu = Integer.parseInt(left.trim());
                int maybeIoa = Integer.parseInt(right.trim());
                if ((maybeAsdu == 1 || maybeAsdu == 9 || maybeAsdu == 40) && maybeIoa > 255) {
                    return fromAsduIoa(maybeAsdu, maybeIoa);
                }
                return new Iec103Point(parseByte(left, "FUN"), parseByte(right, "INF"), Kind.MEASURED_FLOAT);
            }
            throw new IllegalArgumentException(
                    "IEC103 two-part point must be FUN:INF or ASDUid:IOA, got: " + trimmed);
        }
        if (parts.length == 3) {
            String typeTok = parts[0].trim();
            int fun = parseByte(parts[1].trim(), "FUN");
            int inf = parseByte(parts[2].trim(), "INF");
            return new Iec103Point(fun, inf, kindFromToken(typeTok));
        }
        throw new IllegalArgumentException(
                "IEC103 point must be FUN:INF, ASDU:FUN:INF, or ASDUid:IOA, got: " + trimmed);
    }

    private static Iec103Point fromAsduIoa(int asdu, int ioa) {
        if (ioa < 0 || ioa > 0xFFFF) {
            throw new IllegalArgumentException("IEC103 packed IOA out of range: " + ioa);
        }
        int fun = (ioa >>> 8) & 0xFF;
        int inf = ioa & 0xFF;
        return new Iec103Point(fun, inf, kindFromAsdu(asdu));
    }

    private static Kind kindFromAsdu(int asdu) {
        return switch (asdu) {
            case 1 -> Kind.STATUS;
            case 9 -> Kind.MEASURANDS_II;
            case 40 -> Kind.MEASURED_FLOAT;
            default -> throw new IllegalArgumentException(
                    "IEC103 ASDU must be 1, 9, or 40 for points, got: " + asdu);
        };
    }

    private static Kind kindFromToken(String token) {
        String upper = token.toUpperCase(Locale.ROOT);
        return switch (upper) {
            case "1", "ASDU1", "STATUS", "DPI", "M_TM_TA_1" -> Kind.STATUS;
            case "9", "ASDU9", "MEAS9", "M_ME_NA_2" -> Kind.MEASURANDS_II;
            case "40", "ASDU40", "MEAS", "FLOAT", "LAB_MEAS" -> Kind.MEASURED_FLOAT;
            default -> throw new IllegalArgumentException(
                    "IEC103 type must be STATUS/1, MEAS9/9, or MEAS/40, got: " + token);
        };
    }

    private static boolean isNamedAsduToken(String token) {
        String upper = token.toUpperCase(Locale.ROOT);
        return upper.startsWith("ASDU")
                || "STATUS".equals(upper) || "DPI".equals(upper) || "M_TM_TA_1".equals(upper)
                || "MEAS".equals(upper) || "MEAS9".equals(upper) || "FLOAT".equals(upper)
                || "LAB_MEAS".equals(upper) || "M_ME_NA_2".equals(upper);
    }

    private static int parseAsduNumber(String token) {
        String upper = token.toUpperCase(Locale.ROOT);
        return switch (upper) {
            case "1", "ASDU1", "STATUS", "DPI", "M_TM_TA_1" -> 1;
            case "9", "ASDU9", "MEAS9", "M_ME_NA_2" -> 9;
            case "40", "ASDU40", "MEAS", "FLOAT", "LAB_MEAS" -> 40;
            default -> {
                if (upper.startsWith("ASDU")) {
                    yield Integer.parseInt(upper.substring(4));
                }
                yield Integer.parseInt(token.trim());
            }
        };
    }

    private static boolean isNumeric(String token) {
        try {
            Integer.parseInt(token.trim());
            return true;
        } catch (NumberFormatException e) {
            return false;
        }
    }

    private static int parseByte(String token, String label) {
        try {
            int value = Integer.parseInt(token.trim());
            if (value < 0 || value > 255) {
                throw new IllegalArgumentException(label + " out of range: " + value);
            }
            return value;
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("Invalid IEC103 " + label + ": " + token, e);
        }
    }

    private static int parseByteOrIoa(String token, String label) {
        try {
            return Integer.parseInt(token.trim());
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("Invalid IEC103 " + label + ": " + token, e);
        }
    }
}
