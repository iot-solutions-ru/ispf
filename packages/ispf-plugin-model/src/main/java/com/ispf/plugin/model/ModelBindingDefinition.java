package com.ispf.plugin.model;

/**
 * Computed binding attached by a model.
 */
public record ModelBindingDefinition(
        String targetVariable,
        String expression
) {
}
