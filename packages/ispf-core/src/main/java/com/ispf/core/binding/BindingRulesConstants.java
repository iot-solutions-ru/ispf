package com.ispf.core.binding;

/**
 * Platform-managed storage for object binding rules.
 */
public final class BindingRulesConstants {

    public static final String RULES_VARIABLE = "@bindingRules";

    private BindingRulesConstants() {
    }

    public static boolean isReservedVariable(String name) {
        return RULES_VARIABLE.equals(name);
    }
}
