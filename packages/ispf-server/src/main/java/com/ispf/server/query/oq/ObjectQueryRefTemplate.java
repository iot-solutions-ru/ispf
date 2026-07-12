package com.ispf.server.query.oq;

import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class ObjectQueryRefTemplate {

    private static final Pattern ALIAS_PLACEHOLDER = Pattern.compile("\\{([A-Za-z_][A-Za-z0-9_]*)\\}");

    private ObjectQueryRefTemplate() {
    }

    public static String substitute(String template, Map<String, String> aliasPaths, Map<String, Object> rowValues) {
        if (template == null || template.isBlank()) {
            return template;
        }
        Matcher matcher = ALIAS_PLACEHOLDER.matcher(template);
        StringBuilder sb = new StringBuilder();
        while (matcher.find()) {
            String alias = matcher.group(1);
            if ("row".equals(alias) && rowValues != null && !rowValues.isEmpty()) {
                matcher.appendReplacement(sb, Matcher.quoteReplacement(""));
                continue;
            }
            String path = aliasPaths.get(alias);
            if (path == null) {
                throw new IllegalArgumentException("Unknown alias in template: {" + alias + "} in " + template);
            }
            matcher.appendReplacement(sb, Matcher.quoteReplacement(path));
        }
        matcher.appendTail(sb);
        return sb.toString();
    }

    public static boolean isRowFieldRef(String ref) {
        return ref != null && ref.startsWith("{row}/");
    }

    public static String rowFieldName(String ref) {
        if (!isRowFieldRef(ref)) {
            return null;
        }
        return ref.substring("{row}/".length());
    }
}
