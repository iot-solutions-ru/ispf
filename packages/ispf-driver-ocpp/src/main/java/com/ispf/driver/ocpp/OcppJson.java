package com.ispf.driver.ocpp;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Minimal JSON subset for OCPP 1.6 CALL / CALLRESULT arrays (public OCPP-J schema).
 * Flat string/number/boolean object values only — not a general-purpose JSON library.
 */
final class OcppJson {

    private OcppJson() {
    }

    static String call(String uniqueId, String action, Map<String, ?> payload) {
        return "[2," + quote(uniqueId) + "," + quote(action) + "," + object(payload) + "]";
    }

    static String callResult(String uniqueId, Map<String, ?> payload) {
        return "[3," + quote(uniqueId) + "," + object(payload) + "]";
    }

    static String object(Map<String, ?> fields) {
        if (fields == null || fields.isEmpty()) {
            return "{}";
        }
        StringBuilder sb = new StringBuilder(64);
        sb.append('{');
        boolean first = true;
        for (Map.Entry<String, ?> entry : fields.entrySet()) {
            if (!first) {
                sb.append(',');
            }
            first = false;
            sb.append(quote(entry.getKey())).append(':');
            sb.append(value(entry.getValue()));
        }
        sb.append('}');
        return sb.toString();
    }

    private static String value(Object raw) {
        if (raw == null) {
            return "null";
        }
        if (raw instanceof Boolean || raw instanceof Number) {
            return raw.toString();
        }
        return quote(String.valueOf(raw));
    }

    static String quote(String text) {
        StringBuilder sb = new StringBuilder(text.length() + 8);
        sb.append('"');
        for (int i = 0; i < text.length(); i++) {
            char ch = text.charAt(i);
            switch (ch) {
                case '"' -> sb.append("\\\"");
                case '\\' -> sb.append("\\\\");
                case '\n' -> sb.append("\\n");
                case '\r' -> sb.append("\\r");
                case '\t' -> sb.append("\\t");
                default -> sb.append(ch);
            }
        }
        sb.append('"');
        return sb.toString();
    }

    static ParsedMessage parse(String line) {
        Tokenizer tok = new Tokenizer(line.trim());
        tok.expect('[');
        int type = tok.readInt();
        tok.expect(',');
        String uniqueId = tok.readString();
        String action = null;
        Map<String, String> payload = Map.of();
        if (type == 2) {
            tok.expect(',');
            action = tok.readString();
            tok.expect(',');
            payload = tok.readObjectAsStrings();
        } else if (type == 3) {
            tok.expect(',');
            payload = tok.readObjectAsStrings();
        } else if (type == 4) {
            tok.expect(',');
            String errorCode = tok.readString();
            tok.expect(',');
            String errorDescription = tok.readString();
            payload = new LinkedHashMap<>();
            payload.put("errorCode", errorCode);
            payload.put("errorDescription", errorDescription);
            if (tok.peek() == ',') {
                tok.expect(',');
                payload.putAll(tok.readObjectAsStrings());
            }
        } else {
            throw new IllegalArgumentException("Unsupported OCPP messageTypeId=" + type);
        }
        tok.skipWs();
        tok.expect(']');
        return new ParsedMessage(type, uniqueId, action, payload);
    }

    record ParsedMessage(int type, String uniqueId, String action, Map<String, String> payload) {
    }

    private static final class Tokenizer {
        private final String text;
        private int index;

        Tokenizer(String text) {
            this.text = text;
        }

        void skipWs() {
            while (index < text.length() && Character.isWhitespace(text.charAt(index))) {
                index++;
            }
        }

        char peek() {
            skipWs();
            return index < text.length() ? text.charAt(index) : '\0';
        }

        void expect(char expected) {
            skipWs();
            if (index >= text.length() || text.charAt(index) != expected) {
                throw new IllegalArgumentException("Expected '" + expected + "' at " + index + " in " + text);
            }
            index++;
        }

        int readInt() {
            skipWs();
            int start = index;
            if (index < text.length() && text.charAt(index) == '-') {
                index++;
            }
            while (index < text.length() && Character.isDigit(text.charAt(index))) {
                index++;
            }
            if (start == index || (text.charAt(start) == '-' && start + 1 == index)) {
                throw new IllegalArgumentException("Expected int at " + start);
            }
            return Integer.parseInt(text.substring(start, index));
        }

        String readString() {
            skipWs();
            expect('"');
            StringBuilder sb = new StringBuilder();
            while (index < text.length()) {
                char ch = text.charAt(index++);
                if (ch == '"') {
                    return sb.toString();
                }
                if (ch == '\\' && index < text.length()) {
                    char esc = text.charAt(index++);
                    sb.append(switch (esc) {
                        case '"', '\\', '/' -> esc;
                        case 'n' -> '\n';
                        case 'r' -> '\r';
                        case 't' -> '\t';
                        default -> esc;
                    });
                } else {
                    sb.append(ch);
                }
            }
            throw new IllegalArgumentException("Unterminated string");
        }

        Map<String, String> readObjectAsStrings() {
            skipWs();
            expect('{');
            Map<String, String> out = new LinkedHashMap<>();
            skipWs();
            if (peek() == '}') {
                expect('}');
                return out;
            }
            while (true) {
                String key = readString();
                expect(':');
                out.put(key, readScalarAsString());
                skipWs();
                if (peek() == '}') {
                    expect('}');
                    return out;
                }
                expect(',');
            }
        }

        private String readScalarAsString() {
            skipWs();
            char ch = peek();
            if (ch == '"') {
                return readString();
            }
            if (ch == 't' || ch == 'f' || ch == 'n' || ch == '-' || Character.isDigit(ch)) {
                int start = index;
                while (index < text.length()) {
                    char c = text.charAt(index);
                    if (c == ',' || c == '}' || c == ']' || Character.isWhitespace(c)) {
                        break;
                    }
                    index++;
                }
                return text.substring(start, index);
            }
            if (ch == '{' || ch == '[') {
                skipNested();
                return "";
            }
            throw new IllegalArgumentException("Unexpected scalar at " + index);
        }

        private void skipNested() {
            List<Character> stack = new ArrayList<>();
            char first = text.charAt(index++);
            stack.add(first == '{' ? '}' : ']');
            boolean inString = false;
            boolean escape = false;
            while (index < text.length() && !stack.isEmpty()) {
                char c = text.charAt(index++);
                if (inString) {
                    if (escape) {
                        escape = false;
                    } else if (c == '\\') {
                        escape = true;
                    } else if (c == '"') {
                        inString = false;
                    }
                    continue;
                }
                if (c == '"') {
                    inString = true;
                } else if (c == '{' || c == '[') {
                    stack.add(c == '{' ? '}' : ']');
                } else if (c == '}' || c == ']') {
                    stack.remove(stack.size() - 1);
                }
            }
        }
    }
}
