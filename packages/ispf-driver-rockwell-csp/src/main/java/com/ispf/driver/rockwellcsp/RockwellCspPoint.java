package com.ispf.driver.rockwellcsp;

import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Allen-Bradley CSP (PCCC-over-Ethernet) lab data-table point.
 * <p>
 * Accepted forms: {@code N7:0}, {@code F8:1}, {@code B3:0/0} (file type + file number
 * + element, optional bit for binary files).
 */
public record RockwellCspPoint(FileType fileType, int fileNumber, int element, int bit) {

    private static final Pattern PATTERN = Pattern.compile(
            "^([NFB])(\\d+):(\\d+)(?:/(\\d+))?$",
            Pattern.CASE_INSENSITIVE
    );

    public static RockwellCspPoint parse(String raw) {
        if (raw == null || raw.isBlank()) {
            throw new IllegalArgumentException("Rockwell CSP point mapping is blank");
        }
        Matcher matcher = PATTERN.matcher(raw.trim());
        if (!matcher.matches()) {
            throw new IllegalArgumentException(
                    "Rockwell CSP point must be Nf:e, Ff:e, or Bf:e[/b], e.g. N7:0, F8:1, B3:0/0"
            );
        }
        FileType type = FileType.fromToken(matcher.group(1));
        int fileNumber = Integer.parseInt(matcher.group(2));
        int element = Integer.parseInt(matcher.group(3));
        int bit = matcher.group(4) == null ? 0 : Integer.parseInt(matcher.group(4));
        if (fileNumber < 0 || element < 0) {
            throw new IllegalArgumentException("Rockwell CSP file/element must be >= 0");
        }
        if (type != FileType.B && matcher.group(4) != null) {
            throw new IllegalArgumentException("Bit index is only valid for B files");
        }
        if (type == FileType.B && (bit < 0 || bit > 15)) {
            throw new IllegalArgumentException("Rockwell CSP bit index must be 0..15");
        }
        return new RockwellCspPoint(type, fileNumber, element, bit);
    }

    public String deviceLabel() {
        return fileType.token() + fileNumber;
    }

    @Override
    public String toString() {
        String base = fileType.token() + fileNumber + ":" + element;
        return fileType == FileType.B ? base + "/" + bit : base;
    }

    public enum FileType {
        N("N", (byte) 0x89),
        F("F", (byte) 0x8A),
        B("B", (byte) 0x85);

        private final String token;
        private final byte pcccCode;

        FileType(String token, byte pcccCode) {
            this.token = token;
            this.pcccCode = pcccCode;
        }

        public String token() {
            return token;
        }

        public byte pcccCode() {
            return pcccCode;
        }

        static FileType fromToken(String token) {
            String normalized = token.toUpperCase(Locale.ROOT);
            for (FileType type : values()) {
                if (type.token.equals(normalized)) {
                    return type;
                }
            }
            throw new IllegalArgumentException("Unsupported CSP file type: " + token);
        }

        static FileType fromPcccCode(byte code) {
            for (FileType type : values()) {
                if (type.pcccCode == code) {
                    return type;
                }
            }
            throw new IllegalArgumentException(
                    "Unknown CSP file type code 0x" + Integer.toHexString(code & 0xFF));
        }
    }
}
