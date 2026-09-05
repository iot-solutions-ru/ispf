package com.ispf.driver.plcnext;

import java.util.Locale;

/**
 * Minimal JSON helpers for the PLCnext RSC-lab HTTP dialect (JDK only).
 */
final class PlcnextJson {

    private PlcnextJson() {
    }

    static String object(String path, String value) {
        return "{\"path\":" + quote(path) + ",\"value\":" + quote(value) + "}";
    }

    static String quote(String text) {
        StringBuilder sb = new StringBuilder("\"");
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            switch (c) {
                case '"' -> sb.append("\\\"");
                case '\\' -> sb.append("\\\\");
                case '\n' -> sb.append("\\n");
                case '\r' -> sb.append("\\r");
                case '\t' -> sb.append("\\t");
                default -> {
                    if (c < 0x20) {
                        sb.append(String.format(Locale.ROOT, "\\u%04x", (int) c));
                    } else {
                        sb.append(c);
                    }
                }
            }
        }
        return sb.append('"').toString();
    }

    static String extractStringField(String json, String field) {
        if (json == null || json.isBlank()) {
            return "";
        }
        String needle = "\"" + field + "\"";
        int idx = json.indexOf(needle);
        if (idx < 0) {
            return "";
        }
        int colon = json.indexOf(':', idx + needle.length());
        if (colon < 0) {
            return "";
        }
        int i = colon + 1;
        while (i < json.length() && Character.isWhitespace(json.charAt(i))) {
            i++;
        }
        if (i >= json.length()) {
            return "";
        }
        if (json.charAt(i) == '"') {
            StringBuilder sb = new StringBuilder();
            i++;
            while (i < json.length()) {
                char c = json.charAt(i++);
                if (c == '\\' && i < json.length()) {
                    char esc = json.charAt(i++);
                    sb.append(switch (esc) {
                        case 'n' -> '\n';
                        case 'r' -> '\r';
                        case 't' -> '\t';
                        case '"' -> '"';
                        case '\\' -> '\\';
                        default -> esc;
                    });
                } else if (c == '"') {
                    break;
                } else {
                    sb.append(c);
                }
            }
            return sb.toString();
        }
        int end = i;
        while (end < json.length()) {
            char c = json.charAt(end);
            if (c == ',' || c == '}' || Character.isWhitespace(c)) {
                break;
            }
            end++;
        }
        return json.substring(i, end);
    }
}
