package com.ispf.core.binding;

/**
 * Where a binding rule writes its computed result.
 */
public record BindingTarget(
        String kind,
        String variableName,
        String field,
        String path,
        String eventName,
        String ref
) {

    public BindingTarget {
        kind = BindingTargetKind.normalize(kind);
        if (field == null || field.isBlank()) {
            field = "value";
        }
        if (ref != null && ref.isBlank()) {
            ref = null;
        }
    }

    /** Legacy constructor — variable target. */
    public BindingTarget(String variableName, String field) {
        this(BindingTargetKind.VARIABLE, variableName, field, null, null, null);
    }

    /** Legacy constructor without ref. */
    public BindingTarget(String kind, String variableName, String field, String path, String eventName) {
        this(kind, variableName, field, path, eventName, null);
    }

    public boolean isVariable() {
        return BindingTargetKind.VARIABLE.equals(kind);
    }

    public boolean isContext() {
        return BindingTargetKind.CONTEXT.equals(kind);
    }

    public boolean isEvent() {
        return BindingTargetKind.EVENT.equals(kind);
    }
}
