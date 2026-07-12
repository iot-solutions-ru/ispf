package com.ispf.expression;

import java.util.List;
import java.util.Map;

public final class OqRowsJson {

    private OqRowsJson() {
    }

    public static String encode(List<Map<String, Object>> rows) {
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < rows.size(); i++) {
            if (i > 0) {
                sb.append(',');
            }
            sb.append(encodeObject(rows.get(i)));
        }
        sb.append(']');
        return sb.toString();
    }

    private static String encodeObject(Map<String, Object> row) {
        StringBuilder sb = new StringBuilder("{");
        int i = 0;
        for (Map.Entry<String, Object> entry : row.entrySet()) {
            if (i++ > 0) {
                sb.append(',');
            }
            sb.append('"').append(escape(entry.getKey())).append("\":");
            sb.append(encodeValue(entry.getValue()));
        }
        sb.append('}');
        return sb.toString();
    }

    private static String encodeValue(Object value) {
        if (value == null) {
            return "null";
        }
        if (value instanceof Number || value instanceof Boolean) {
            return String.valueOf(value);
        }
        return "\"" + escape(String.valueOf(value)) + "\"";
    }

    private static String escape(String value) {
        return value.replace("\\", "\\\\").replace("\"", "\\\"");
    }
}
