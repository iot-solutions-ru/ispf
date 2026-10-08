package com.ispf.core.binding;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class BindingCascadeAnalyzerTest {

    @Test
    void warnsOnWriteActivateCycle() {
        BindingRule left = rule(
                "left",
                10,
                new BindingActivators(false, List.of(BindingVariableRef.local("x")), null, 0),
                "1",
                new BindingTarget("y", "value")
        );
        BindingRule right = rule(
                "right",
                20,
                new BindingActivators(false, List.of(BindingVariableRef.local("y")), null, 0),
                "1",
                new BindingTarget("x", "value")
        );

        List<String> warnings = BindingCascadeAnalyzer.warnings(List.of(left, right));

        assertThat(warnings).anyMatch(w -> w.contains("write/activate cycle") && w.contains("left"));
    }

    @Test
    void warnsOnHeavyExpression() {
        String heavy = "x".repeat(BindingCascadeAnalyzer.HEAVY_EXPRESSION_CHARS);
        BindingRule rule = rule(
                "heavy",
                10,
                BindingActivators.onLocalChange(),
                heavy,
                new BindingTarget("out", "value")
        );

        assertThat(BindingCascadeAnalyzer.warnings(List.of(rule)))
                .anyMatch(w -> w.contains("heavy expression"));
    }

    private static BindingRule rule(
            String id,
            int order,
            BindingActivators activators,
            String expression,
            BindingTarget target
    ) {
        return new BindingRule(id, id, true, order, activators, "", expression, target);
    }
}
