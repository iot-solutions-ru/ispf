package com.ispf.driver.flexible;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Declarative response field extractors for the flexible exchange pipeline.
 */
final class FlexExtractor {

    enum Type {
        REGEX,
        ASCII_HEX_FLOAT,
        SLICE,
        LITERAL
    }

    private final Type type;
    private final String[] args;

    private FlexExtractor(Type type, String... args) {
        this.type = type;
        this.args = args;
    }

    static FlexExtractor parse(String segment) {
        if (segment == null || segment.isBlank()) {
            throw new IllegalArgumentException("Extractor segment is blank");
        }
        String trimmed = segment.trim();
        if (!trimmed.startsWith("extract:")) {
            throw new IllegalArgumentException("Expected extract: segment, got: " + trimmed);
        }
        String body = trimmed.substring("extract:".length());
        String[] parts = body.split(":", -1);
        if (parts.length == 0 || parts[0].isBlank()) {
            throw new IllegalArgumentException("Extractor type is blank");
        }
        return switch (parts[0]) {
            case "regex" -> {
                if (parts.length < 3) {
                    throw new IllegalArgumentException("extract:regex requires pattern and group index");
                }
                yield new FlexExtractor(Type.REGEX, parts[1], parts[2]);
            }
            case "asciiHexFloat" -> {
                if (parts.length < 2) {
                    throw new IllegalArgumentException("extract:asciiHexFloat requires index");
                }
                if (parts.length >= 4 && "after".equals(parts[2])) {
                    yield new FlexExtractor(Type.ASCII_HEX_FLOAT, parts[1], parts[3]);
                }
                yield new FlexExtractor(Type.ASCII_HEX_FLOAT, parts[1]);
            }
            case "slice" -> {
                if (parts.length < 3) {
                    throw new IllegalArgumentException("extract:slice requires start and length");
                }
                yield new FlexExtractor(Type.SLICE, parts[1], parts[2]);
            }
            case "literal" -> {
                if (parts.length < 2) {
                    throw new IllegalArgumentException("extract:literal requires value");
                }
                yield new FlexExtractor(Type.LITERAL, parts[1]);
            }
            default -> throw new IllegalArgumentException("Unknown extractor type: " + parts[0]);
        };
    }

    String extract(String payload) {
        return switch (type) {
            case REGEX -> extractRegex(payload);
            case ASCII_HEX_FLOAT -> extractAsciiHexFloat(payload);
            case SLICE -> extractSlice(payload);
            case LITERAL -> args[0];
        };
    }

    private String extractRegex(String payload) {
        Pattern pattern = Pattern.compile(args[0], Pattern.DOTALL);
        Matcher matcher = pattern.matcher(payload);
        int group = Integer.parseInt(args[1]);
        if (matcher.find() && matcher.groupCount() >= group) {
            return matcher.group(group);
        }
        return "";
    }

    private String extractSlice(String payload) {
        int start = Integer.parseInt(args[0]);
        int length = Integer.parseInt(args[1]);
        if (start < 0 || start >= payload.length()) {
            return "";
        }
        int end = Math.min(payload.length(), start + length);
        return payload.substring(start, end);
    }

    private String extractAsciiHexFloat(String payload) {
        int index = Integer.parseInt(args[0]);
        String scan = payload;
        if (args.length >= 2) {
            String marker = args[1];
            int markerIndex = payload.indexOf(marker);
            if (markerIndex < 0) {
                return "";
            }
            scan = payload.substring(markerIndex + marker.length());
        }
        List<String> floats = scanAsciiHexFloats(scan);
        if (index < 0 || index >= floats.size()) {
            return "";
        }
        return floats.get(index);
    }

    static List<String> scanAsciiHexFloats(String payload) {
        List<String> values = new ArrayList<>();
        Pattern pattern = Pattern.compile("[0-9A-Fa-f]{8}");
        Matcher matcher = pattern.matcher(payload);
        while (matcher.find()) {
            String hex = matcher.group();
            try {
                int bits = (int) Long.parseLong(hex, 16);
                values.add(Float.toString(Float.intBitsToFloat(bits)));
            } catch (NumberFormatException ignored) {
                // skip invalid sequences
            }
        }
        return values;
    }
}
