package com.ispf.analytics.spi;

/**
 * Metadata for a single analytics function parameter.
 *
 * @param name parameter name used by syntax/helper docs
 * @param type logical parameter type (for example: number, string, duration)
 * @param required whether caller must provide this parameter
 * @param description human-readable parameter description
 */
public record AnalyticsFunctionParameterDescriptor(
        String name,
        String type,
        boolean required,
        String description
) {
}
