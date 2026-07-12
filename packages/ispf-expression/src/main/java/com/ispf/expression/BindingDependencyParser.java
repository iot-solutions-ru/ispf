package com.ispf.expression;

import com.ispf.core.binding.BindingVariableRef;
import com.ispf.core.ref.PlatformRef;
import com.ispf.core.ref.PlatformRefParser;

import java.util.LinkedHashSet;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Extracts cross-object dependencies from binding expressions (PlatformRef slash grammar).
 */
public final class BindingDependencyParser {

    private BindingDependencyParser() {
    }

    public static Set<BindingVariableRef> parseRefAtDependencies(String expression) {
        Set<BindingVariableRef> refs = new LinkedHashSet<>();
        for (PlatformRef ref : PlatformRefParser.extractRefsFromExpression(expression)) {
            if (!ref.isVariable()) {
                continue;
            }
            if (ref.isCurrentObject()) {
                refs.add(BindingVariableRef.local(ref.name()));
            } else {
                refs.add(BindingVariableRef.remote(ref.object(), ref.name()));
            }
        }
        return refs;
    }

    public static Set<PlatformRef> parseAllRefs(String expression) {
        return PlatformRefParser.extractRefsFromExpression(expression);
    }

    public static Set<BindingVariableRef> parseEventDependencies(String expression) {
        return PlatformRefParser.extractRefsFromExpression(expression).stream()
                .filter(PlatformRef::isEvent)
                .map(ref -> BindingVariableRef.remote(
                        ref.isCurrentObject() ? BindingVariableRef.SELF : ref.object(),
                        ref.name()
                ))
                .collect(Collectors.toCollection(LinkedHashSet::new));
    }
}
