package com.ispf.driver.odata;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Minimal OData JSON v4 subset parser. JDK-only clean-room. */
final class OdataJson {

    private OdataJson() {
    }

    static Object parse(String text) {
        if (text == null) {
            return null;
        }
        String trimmed = text.trim();
        if (trimmed.isEmpty()) {
            return "";
        }
        Parser parser = new Parser(trimmed);
        Object value = parser.parseValue();
        parser.skipWs();
        if (!parser.eof()) {
            throw new IllegalArgumentException("Trailing JSON at index " + parser.index);
        }
        return value;
    }

    static String extract(Object root, String property) {
        if (root == null) {
            return "";
        }
        if (property == null || property.isBlank()) {
            if (root instanceof Map<?, ?> map && map.containsKey("value")) {
                return stringify(map.get("value"));
            }
            return stringify(root);
        }
        if (root instanceof Map<?, ?> map) {
            Object valueNode = map.get("value");
            if (valueNode instanceof List<?> list) {
                if (list.isEmpty()) {
                    return "";
                }
                Object first = list.get(0);
                if (first instanceof Map<?, ?> entity) {
                    return stringify(entity.get(property));
                }
                return "";
            }
            if (map.containsKey(property)) {
                return stringify(map.get(property));
            }
            if (valueNode != null && !(valueNode instanceof Map) && !(valueNode instanceof List)) {
                return stringify(valueNode);
            }
        }
        return "";
    }

    static String stringify(Object value) {
        if (value == null) {
            return "";
        }
        if (value instanceof String s) {
            return s;
        }
        if (value instanceof Number || value instanceof Boolean) {
            return String.valueOf(value);
        }
        if (value instanceof Map<?, ?> map) {
            return toJsonObject(castMap(map));
        }
        if (value instanceof List<?> list) {
            return toJsonArray(list);
        }
        return String.valueOf(value);
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> castMap(Map<?, ?> map) {
        return (Map<String, Object>) map;
    }

    static String toJsonObject(Map<String, Object> map) {
        StringBuilder sb = new StringBuilder("{");
        boolean first = true;
        for (Map.Entry<String, Object> entry : map.entrySet()) {
            if (!first) {
                sb.append(',');
            }
            first = false;
            sb.append('"').append(escape(entry.getKey())).append('"').append(':');
            append(sb, entry.getValue());
        }
        sb.append('}');
        return sb.toString();
    }

    private static String toJsonArray(List<?> list) {
        StringBuilder sb = new StringBuilder("[");
        boolean first = true;
        for (Object item : list) {
            if (!first) {
                sb.append(',');
            }
            first = false;
            append(sb, item);
        }
        sb.append(']');
        return sb.toString();
    }

    @SuppressWarnings("unchecked")
    private static void append(StringBuilder sb, Object value) {
        if (value == null) {
            sb.append("null");
        } else if (value instanceof Number || value instanceof Boolean) {
            sb.append(value);
        } else if (value instanceof Map<?, ?> map) {
            sb.append(toJsonObject(castMap(map)));
        } else if (value instanceof List<?> list) {
            sb.append(toJsonArray(list));
        } else {
            sb.append('"').append(escape(String.valueOf(value))).append('"');
        }
    }

    private static String escape(String text) {
        return text.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n").replace("\r", "\\r").replace("\t", "\\t");
    }

    private static final class Parser {
        private final String text;
        private int index;

        Parser(String text) {
            this.text = text;
        }

        boolean eof() {
            return index >= text.length();
        }

        void skipWs() {
            while (index < text.length() && Character.isWhitespace(text.charAt(index))) {
                index++;
            }
        }

        Object parseValue() {
            skipWs();
            if (eof()) {
                throw new IllegalArgumentException("Unexpected end of JSON");
            }
            char c = text.charAt(index);
            return switch (c) {
                case '{' -> parseObject();
                case '[' -> parseArray();
                case '"' -> parseString();
                case 't' -> parseLiteral("true", Boolean.TRUE);
                case 'f' -> parseLiteral("false", Boolean.FALSE);
                case 'n' -> parseLiteral("null", null);
                default -> parseNumber();
            };
        }

        private Map<String, Object> parseObject() {
            expect('{');
            Map<String, Object> map = new LinkedHashMap<>();
            skipWs();
            if (peek('}')) {
                index++;
                return map;
            }
            while (true) {
                skipWs();
                String key = parseString();
                skipWs();
                expect(':');
                map.put(key, parseValue());
                skipWs();
                if (peek('}')) {
                    index++;
                    return map;
                }
                expect(',');
            }
        }

        private List<Object> parseArray() {
            expect('[');
            List<Object> list = new ArrayList<>();
            skipWs();
            if (peek(']')) {
                index++;
                return list;
            }
            while (true) {
                list.add(parseValue());
                skipWs();
                if (peek(']')) {
                    index++;
                    return list;
                }
                expect(',');
            }
        }

        private String parseString() {
            expect('"');
            StringBuilder sb = new StringBuilder();
            while (!eof()) {
                char c = text.charAt(index++);
                if (c == '"') {
                    return sb.toString();
                }
                if (c == '\\') {
                    if (eof()) {
                        throw new IllegalArgumentException("Bad escape");
                    }
                    char esc = text.charAt(index++);
                    sb.append(switch (esc) {
                        case '"', '\\', '/' -> esc;
                        case 'b' -> '\b';
                        case 'f' -> '\f';
                        case 'n' -> '\n';
                        case 'r' -> '\r';
                        case 't' -> '\t';
                        case 'u' -> parseUnicode();
                        default -> throw new IllegalArgumentException("Bad escape");
                    });
                } else {
                    sb.append(c);
                }
            }
            throw new IllegalArgumentException("Unterminated string");
        }

        private char parseUnicode() {
            int code = Integer.parseInt(text.substring(index, index + 4), 16);
            index += 4;
            return (char) code;
        }

        private Object parseLiteral(String literal, Object value) {
            if (!text.startsWith(literal, index)) {
                throw new IllegalArgumentException("Expected " + literal);
            }
            index += literal.length();
            return value;
        }

        private Number parseNumber() {
            int start = index;
            if (peek('-')) {
                index++;
            }
            while (!eof() && Character.isDigit(text.charAt(index))) {
                index++;
            }
            boolean decimal = false;
            if (peek('.')) {
                decimal = true;
                index++;
                while (!eof() && Character.isDigit(text.charAt(index))) {
                    index++;
                }
            }
            if (peek('e') || peek('E')) {
                decimal = true;
                index++;
                if (peek('+') || peek('-')) {
                    index++;
                }
                while (!eof() && Character.isDigit(text.charAt(index))) {
                    index++;
                }
            }
            String raw = text.substring(start, index);
            if (decimal) {
                return Double.parseDouble(raw);
            }
            long value = Long.parseLong(raw);
            if (value >= Integer.MIN_VALUE && value <= Integer.MAX_VALUE) {
                return (int) value;
            }
            return value;
        }

        private void expect(char c) {
            skipWs();
            if (eof() || text.charAt(index) != c) {
                throw new IllegalArgumentException("Expected '" + c + "'");
            }
            index++;
        }

        private boolean peek(char c) {
            return !eof() && text.charAt(index) == c;
        }
    }
}
