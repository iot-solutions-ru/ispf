package com.ispf.core.binding;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Static checks that catch binding footguns (cycles, heavy expressions) before runtime.
 * <p>
 * ISPF already mitigates sequential cascade pain with ordered multi-pass evaluation
 * ({@code BindingRuleEngine} pass/depth limits). This analyzer warns about remaining
 * ping-pong graphs and oversized expressions that belong in hub functions, not rules.
 */
public final class BindingCascadeAnalyzer {

    public static final int HEAVY_EXPRESSION_CHARS = 400;

    private BindingCascadeAnalyzer() {
    }

    public static List<String> warnings(List<BindingRule> rules) {
        if (rules == null || rules.isEmpty()) {
            return List.of();
        }
        List<String> warnings = new ArrayList<>();
        List<BindingRule> reactive = rules.stream()
                .filter(BindingRule::enabled)
                .filter(BindingRule::isReactive)
                .toList();

        for (BindingRule rule : reactive) {
            String expression = rule.expression() == null ? "" : rule.expression();
            String condition = rule.condition() == null ? "" : rule.condition();
            if (expression.length() >= HEAVY_EXPRESSION_CHARS
                    || condition.length() >= HEAVY_EXPRESSION_CHARS) {
                warnings.add(
                        "Rule '" + rule.id() + "' has a heavy expression/condition (≥"
                                + HEAVY_EXPRESSION_CHARS
                                + " chars). Move branching logic to a hub function and keep the rule thin "
                                + "(docs/en/anti-patterns.md)."
                );
            }
        }

        for (int i = 0; i < reactive.size(); i++) {
            BindingRule left = reactive.get(i);
            Set<String> leftWrites = writeVariables(left);
            Set<String> leftReads = activatorVariables(left);
            if (leftWrites.isEmpty()) {
                continue;
            }
            for (int j = 0; j < reactive.size(); j++) {
                if (i == j) {
                    continue;
                }
                BindingRule right = reactive.get(j);
                Set<String> rightWrites = writeVariables(right);
                Set<String> rightReads = activatorVariables(right);
                boolean leftFeedsRight = overlaps(leftWrites, rightReads);
                boolean rightFeedsLeft = overlaps(rightWrites, leftReads);
                if (leftFeedsRight && rightFeedsLeft) {
                    warnings.add(
                            "Rules '" + left.id() + "' and '" + right.id()
                                    + "' form a write/activate cycle on the same object. "
                                    + "Prefer one orchestrating rule or async=false with clear order; "
                                    + "runtime stops after pass/depth limits (docs/en/bindings.md#execution)."
                    );
                }
            }
        }
        // Deduplicate cycle warnings (A↔B reported twice).
        return List.copyOf(new LinkedHashSet<>(warnings));
    }

    private static boolean overlaps(Set<String> writes, Set<String> reads) {
        if (writes.isEmpty() || reads.isEmpty()) {
            return false;
        }
        if (reads.contains(BindingVariableRef.ANY)) {
            return true;
        }
        for (String write : writes) {
            if (reads.contains(write)) {
                return true;
            }
        }
        return false;
    }

    private static Set<String> writeVariables(BindingRule rule) {
        BindingTarget target = rule.target();
        if (target == null || !target.isVariable()) {
            return Set.of();
        }
        if (target.ref() != null && !target.ref().isBlank()) {
            // Cross-object writes do not cascade on this object's activator index.
            return Set.of();
        }
        String name = target.variableName();
        if (name == null || name.isBlank()) {
            return Set.of();
        }
        return Set.of(name);
    }

    private static Set<String> activatorVariables(BindingRule rule) {
        Set<String> names = new LinkedHashSet<>();
        for (BindingVariableRef ref : rule.activators().onVariableChange()) {
            BindingVariableRef normalized = ref.normalize();
            String path = normalized.objectPath();
            if (path != null
                    && !path.isBlank()
                    && !BindingVariableRef.SELF.equals(path)
                    && !BindingVariableRef.ANY.equals(path)) {
                continue; // remote activator — different object
            }
            names.add(normalized.variableName());
        }
        return names;
    }
}
