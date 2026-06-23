package com.ispf.expression;

import com.ispf.core.binding.BindingVariableRef;

import java.util.LinkedHashSet;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Extracts cross-object dependencies from binding expressions (e.g. {@code refAt}).
 */
public final class BindingDependencyParser {

    private static final Pattern REF_AT = Pattern.compile(
            "refAt\\(\\s*\"([^\"]+)\"\\s*,\\s*([A-Za-z_][A-Za-z0-9_]*)"
    );

    private BindingDependencyParser() {
    }

    public static Set<BindingVariableRef> parseRefAtDependencies(String expression) {
        Set<BindingVariableRef> refs = new LinkedHashSet<>();
        if (expression == null || expression.isBlank()) {
            return refs;
        }
        Matcher matcher = REF_AT.matcher(expression);
        while (matcher.find()) {
            refs.add(BindingVariableRef.remote(matcher.group(1), matcher.group(2)));
        }
        return refs;
    }
}
