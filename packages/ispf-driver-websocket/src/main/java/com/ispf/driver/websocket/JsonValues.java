package com.ispf.driver.websocket;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * JDK-only JSON reader for subscribe payloads. Paths look like {@code $.msg.transforms.0.child_frame_id}.
 * {@code length} on an array returns its size. {@code round:<digits>:$.path} rounds a number.
 * {@code @state} is the open-socket marker used by the driver after a successful read.
 */
final class JsonValues {

    private JsonValues() {
    }

    static Object parse(String json) {
        if (json == null || json.isBlank()) {
            throw new IllegalArgumentException("Empty WebSocket message");
        }
        Parser parser = new Parser(json);
        Object value = parser.parseValue();
        parser.skipWs();
        if (!parser.eof()) {
            throw new IllegalArgumentException("Trailing data in WebSocket message");
        }
        return value;
    }

    static String readMapping(Object root, String mapping) {
        if (mapping == null || mapping.isBlank()) {
            return "";
        }
        String spec = mapping.trim();
        if ("@state".equals(spec)) {
            return "Open";
        }
        int scale = -1;
        String path = spec;
        if (spec.startsWith("round:")) {
            int separator = spec.indexOf(':', "round:".length());
            if (separator < 0) {
                throw new IllegalArgumentException("Point mapping must look like round:<digits>:$.path");
            }
            scale = Integer.parseInt(spec.substring("round:".length(), separator).trim());
            path = spec.substring(separator + 1).trim();
        }
        String raw = extract(root, path);
        if (raw == null) {
            return "";
        }
        if (scale >= 0) {
            return round(raw, scale);
        }
        return raw;
    }

    private static String extract(Object root, String path) {
        String spec = path == null ? "" : path.trim();
        if ("$".equals(spec)) {
            return scalar(root);
        }
        if (!spec.startsWith("$.")) {
            throw new IllegalArgumentException("Point mapping must be @state, $.path or round:<digits>:$.path");
        }
        Object current = root;
        for (String segment : spec.substring(2).split("\\.", -1)) {
            if (segment.isEmpty()) {
                throw new IllegalArgumentException("Empty JSON path segment in " + spec);
            }
            if (current == null) {
                return null;
            }
            if (current instanceof List<?> list && "length".equals(segment)) {
                return Integer.toString(list.size());
            }
            if (current instanceof Map<?, ?> map) {
                current = map.get(segment);
                continue;
            }
            if (current instanceof List<?> list && isIndex(segment)) {
                int index = Integer.parseInt(segment);
                if (index < 0 || index >= list.size()) {
                    return null;
                }
                current = list.get(index);
                continue;
            }
            return null;
        }
        return scalar(current);
    }

    private static String scalar(Object value) {
        return value instanceof String text ? text : null;
    }

    private static String round(String raw, int scale) {
        if (scale < 0 || scale > 8) {
            throw new IllegalArgumentException("round scale must be from 0 to 8");
        }
        try {
            return new BigDecimal(raw).setScale(scale, RoundingMode.HALF_UP).stripTrailingZeros().toPlainString();
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("Cannot round '" + raw + "'", e);
        }
    }

    private static boolean isIndex(String segment) {
        if (segment.isEmpty()) {
            return false;
        }
        for (int i = 0; i < segment.length(); i++) {
            if (!Character.isDigit(segment.charAt(i))) {
                return false;
            }
        }
        return true;
    }

    private static final class Parser {
        private final String text;
        private int index;

        private Parser(String text) {
            this.text = text;
        }

        private boolean eof() {
            return index >= text.length();
        }

        private void skipWs() {
            while (!eof() && Character.isWhitespace(text.charAt(index))) {
                index++;
            }
        }

        private Object parseValue() {
            skipWs();
            if (eof()) {
                throw new IllegalArgumentException("Unexpected end of JSON");
            }
            return switch (text.charAt(index)) {
                case '{' -> parseObject();
                case '[' -> parseArray();
                case '"' -> parseString();
                case 't' -> literal("true", "true");
                case 'f' -> literal("false", "false");
                case 'n' -> literal("null", null);
                default -> parseNumber();
            };
        }

        private Object literal(String word, Object value) {
            if (!text.startsWith(word, index)) {
                throw new IllegalArgumentException("Invalid JSON literal");
            }
            index += word.length();
            return value;
        }

        private Map<String, Object> parseObject() {
            expect('{');
            Map<String, Object> map = new LinkedHashMap<>();
            skipWs();
            if (consume('}')) {
                return map;
            }
            while (true) {
                skipWs();
                String key = parseString();
                skipWs();
                expect(':');
                map.put(key, parseValue());
                skipWs();
                if (consume('}')) {
                    return map;
                }
                expect(',');
            }
        }

        private List<Object> parseArray() {
            expect('[');
            List<Object> list = new ArrayList<>();
            skipWs();
            if (consume(']')) {
                return list;
            }
            while (true) {
                list.add(parseValue());
                skipWs();
                if (consume(']')) {
                    return list;
                }
                expect(',');
            }
        }

        private String parseString() {
            if (eof() || text.charAt(index) != '"') {
                throw new IllegalArgumentException("Expected JSON string");
            }
            index++;
            StringBuilder value = new StringBuilder();
            while (!eof()) {
                char current = text.charAt(index++);
                if (current == '"') {
                    return value.toString();
                }
                if (current != '\\') {
                    value.append(current);
                    continue;
                }
                if (eof()) {
                    throw new IllegalArgumentException("Truncated JSON escape");
                }
                char escaped = text.charAt(index++);
                switch (escaped) {
                    case '"', '\\', '/' -> value.append(escaped);
                    case 'b' -> value.append('\b');
                    case 'f' -> value.append('\f');
                    case 'n' -> value.append('\n');
                    case 'r' -> value.append('\r');
                    case 't' -> value.append('\t');
                    case 'u' -> value.append(parseHex());
                    default -> throw new IllegalArgumentException("Invalid JSON escape");
                }
            }
            throw new IllegalArgumentException("Unterminated JSON string");
        }

        private char parseHex() {
            if (index + 4 > text.length()) {
                throw new IllegalArgumentException("Truncated JSON unicode escape");
            }
            int code = 0;
            for (int n = 0; n < 4; n++) {
                int digit = Character.digit(text.charAt(index++), 16);
                if (digit < 0) {
                    throw new IllegalArgumentException("Invalid JSON unicode escape");
                }
                code = (code << 4) | digit;
            }
            return (char) code;
        }

        private String parseNumber() {
            int start = index;
            if (!eof() && text.charAt(index) == '-') {
                index++;
            }
            if (eof() || !Character.isDigit(text.charAt(index))) {
                throw new IllegalArgumentException("Invalid JSON number");
            }
            if (text.charAt(index) == '0') {
                index++;
            } else {
                while (!eof() && Character.isDigit(text.charAt(index))) {
                    index++;
                }
            }
            if (!eof() && text.charAt(index) == '.') {
                index++;
                if (eof() || !Character.isDigit(text.charAt(index))) {
                    throw new IllegalArgumentException("Invalid JSON number");
                }
                while (!eof() && Character.isDigit(text.charAt(index))) {
                    index++;
                }
            }
            if (!eof() && (text.charAt(index) == 'e' || text.charAt(index) == 'E')) {
                index++;
                if (!eof() && (text.charAt(index) == '+' || text.charAt(index) == '-')) {
                    index++;
                }
                if (eof() || !Character.isDigit(text.charAt(index))) {
                    throw new IllegalArgumentException("Invalid JSON number");
                }
                while (!eof() && Character.isDigit(text.charAt(index))) {
                    index++;
                }
            }
            return text.substring(start, index);
        }

        private void expect(char symbol) {
            skipWs();
            if (!consume(symbol)) {
                throw new IllegalArgumentException("Expected '" + symbol + "' in JSON");
            }
        }

        private boolean consume(char symbol) {
            if (eof() || text.charAt(index) != symbol) {
                return false;
            }
            index++;
            return true;
        }
    }
}
