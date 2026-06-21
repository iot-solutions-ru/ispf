package com.ispf.expression;

/**
 * Validates CEL binding expressions, including built-in platform bindings.
 */
public final class BindingExpressionValidator {

    private static final ExpressionEngine ENGINE = new ExpressionEngine();

    private BindingExpressionValidator() {
    }

    public static void validateOrThrow(String expression) {
        if (expression == null || expression.isBlank()) {
            return;
        }
        String trimmed = expression.trim();
        if (CounterRateBinding.matches(trimmed)) {
            return;
        }
        ENGINE.compile(trimmed);
    }
}
