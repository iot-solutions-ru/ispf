package com.ispf.expression;

import com.ispf.core.object.PlatformObject;

import java.util.Optional;

/**
 * Built-in platform binding function (non-CEL), e.g. {@code counterRate(...)}.
 */
public interface PlatformBinding {

    boolean matches(String expression);

    Optional<Object> evaluate(
            PlatformObject object,
            String targetVariable,
            String expression,
            BindingEvaluationContext context
    );

    default void clearStateForTests() {
    }
}
