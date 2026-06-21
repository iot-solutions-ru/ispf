package com.ispf.server.object;

/**
 * Reserved system variables managed by the platform (hidden from operator UI).
 */
public final class BindingStateVariables {

    public static final String BINDING_STATE = "@bindingState";

    private BindingStateVariables() {
    }

    public static boolean isReserved(String name) {
        return BINDING_STATE.equals(name);
    }
}
