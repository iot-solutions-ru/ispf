package com.ispf.server.object.pubsub;

/**
 * Subscribers for a single {@code (objectPath, variableName)} change (ADR-0024).
 */
public record VariableChangeInterest(
        boolean historian,
        boolean bindings,
        boolean alerts,
        boolean workflows,
        boolean workflowIndex,
        boolean uiRefresh
) {
    public static final VariableChangeInterest NONE =
            new VariableChangeInterest(false, false, false, false, false, false);

    public boolean hasAny() {
        return historian || bindings || alerts || workflows || workflowIndex || uiRefresh;
    }

    public boolean automation() {
        return bindings || alerts || workflows || workflowIndex;
    }
}
