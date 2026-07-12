package com.ispf.expression;

import com.ispf.core.object.PlatformObject;

import java.util.Optional;

final class ObjectQueryBindingHelper {

    private ObjectQueryBindingHelper() {
    }

    static Optional<String> resolveSpec(String specArg, PlatformObject object, BindingEvaluationContext context) {
        return context.resolveObjectQuerySpec(specArg, object);
    }
}
