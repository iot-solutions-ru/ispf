package com.ispf.expression;

import java.util.List;
import java.util.Optional;

/**
 * Registry of built-in platform binding functions.
 */
public final class PlatformBindingRegistry {

    private static final List<PlatformBinding> BINDINGS = List.of(
            CallFunctionAtBinding.INSTANCE,
            RefAtBinding.INSTANCE,
            CounterRateBinding.INSTANCE,
            CounterDeltaBinding.INSTANCE,
            CallFunctionBinding.INSTANCE,
            MovingAvgBinding.INSTANCE,
            MovingMinBinding.INSTANCE,
            MovingMaxBinding.INSTANCE,
            DeadbandBinding.INSTANCE,
            HysteresisBinding.INSTANCE,
            RateBinding.INSTANCE,
            UnitConvertBinding.INSTANCE,
            SelectFieldBinding.INSTANCE,
            ScaleBinding.INSTANCE,
            ClampBinding.INSTANCE,
            FormatBinding.INSTANCE,
            DeltaBinding.INSTANCE
    );

    private PlatformBindingRegistry() {
    }

    public static Optional<PlatformBinding> find(String expression) {
        if (expression == null) {
            return Optional.empty();
        }
        String trimmed = expression.trim();
        for (PlatformBinding binding : BINDINGS) {
            if (binding.matches(trimmed)) {
                return Optional.of(binding);
            }
        }
        return Optional.empty();
    }

    public static boolean matches(String expression) {
        return find(expression).isPresent();
    }

    public static void setBindingStatePort(BindingStatePort bindingStatePort) {
        BindingStateStore.setPort(bindingStatePort);
    }

    static void clearStateForTests() {
        BINDINGS.forEach(PlatformBinding::clearStateForTests);
        BindingStateStore.clearForTests();
    }
}
