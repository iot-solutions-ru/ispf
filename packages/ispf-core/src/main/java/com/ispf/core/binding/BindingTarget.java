package com.ispf.core.binding;

/**
 * Where a binding rule writes its computed result.
 */
public record BindingTarget(String variableName, String field) {

    public BindingTarget {
        if (field == null || field.isBlank()) {
            field = "value";
        }
    }
}
