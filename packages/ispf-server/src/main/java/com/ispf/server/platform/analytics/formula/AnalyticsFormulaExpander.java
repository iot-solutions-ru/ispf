package com.ispf.server.platform.analytics.formula;

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Expands {@code {{param}}} placeholders in Tier B formula templates.
 */
public final class AnalyticsFormulaExpander {

    private static final Pattern PLACEHOLDER = Pattern.compile("\\{\\{([a-zA-Z][a-zA-Z0-9_]*)\\}\\}");

    private AnalyticsFormulaExpander() {
    }

    public static Set<String> detectParameterNames(String expression) {
        if (expression == null || expression.isBlank()) {
            return Set.of();
        }
        Set<String> names = new LinkedHashSet<>();
        Matcher matcher = PLACEHOLDER.matcher(expression);
        while (matcher.find()) {
            names.add(matcher.group(1));
        }
        return Collections.unmodifiableSet(names);
    }

    public static String expand(String expression, Map<String, String> parameters) {
        if (expression == null) {
            return "";
        }
        Map<String, String> resolved = parameters != null ? parameters : Map.of();
        Matcher matcher = PLACEHOLDER.matcher(expression);
        StringBuffer buffer = new StringBuffer();
        while (matcher.find()) {
            String name = matcher.group(1);
            String value = resolved.get(name);
            if (value == null) {
                throw new IllegalArgumentException("Missing formula parameter: " + name);
            }
            matcher.appendReplacement(buffer, Matcher.quoteReplacement(value.trim()));
        }
        matcher.appendTail(buffer);
        return buffer.toString();
    }
}
