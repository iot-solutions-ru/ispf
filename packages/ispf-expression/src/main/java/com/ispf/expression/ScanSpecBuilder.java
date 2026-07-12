package com.ispf.expression;

final class ScanSpecBuilder {

    private ScanSpecBuilder() {
    }

    static String build(String pattern, String ref, String filter) {
        StringBuilder sb = new StringBuilder();
        sb.append("{\"from\":{\"alias\":\"row\",\"sourcePathPattern\":\"")
                .append(escape(pattern))
                .append("\"");
        if (filter != null && !filter.isBlank()) {
            sb.append(",\"filter\":\"")
                    .append(escape(filter))
                    .append("\"");
        }
        sb.append("},\"fields\":[");
        if (ref != null && !ref.isBlank()) {
            String fieldName = fieldNameFromRef(ref);
            sb.append("{\"name\":\"")
                    .append(escape(fieldName))
                    .append("\",\"ref\":\"")
                    .append(escape(ref))
                    .append("\"}");
        } else {
            sb.append("{\"name\":\"path\",\"source\":\"path\",\"alias\":\"row\"}");
        }
        sb.append("]}");
        return sb.toString();
    }

    static String fieldNameFromRef(String ref) {
        if (ref == null || ref.isBlank()) {
            return "value";
        }
        int slash = ref.lastIndexOf('/');
        if (slash >= 0 && slash < ref.length() - 1) {
            return ref.substring(slash + 1);
        }
        return "value";
    }

    private static String escape(String value) {
        return value.replace("\\", "\\\\").replace("\"", "\\\"");
    }
}
