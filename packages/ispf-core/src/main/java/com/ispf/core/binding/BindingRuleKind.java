package com.ispf.core.binding;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

import java.util.Locale;

/**
 * Computation rule kind — reactive bindings vs historian-backed derived tags (ADR-0041).
 */
public enum BindingRuleKind {
    /** Live CEL / read / call — evaluated by {@code BindingRuleEngine}. */
    REACTIVE,
    /** Historian windows (avg, live, …) / CEL — evaluated by analytics engine. */
    HISTORIAN;

    @JsonValue
    public String toJson() {
        return name().toLowerCase(Locale.ROOT);
    }

    @JsonCreator
    public static BindingRuleKind fromJson(String value) {
        if (value == null || value.isBlank()) {
            return REACTIVE;
        }
        return valueOf(value.trim().toUpperCase(Locale.ROOT));
    }
}
