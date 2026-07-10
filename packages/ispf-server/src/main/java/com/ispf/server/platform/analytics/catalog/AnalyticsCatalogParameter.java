package com.ispf.server.platform.analytics.catalog;

/**
 * Declarative parameter metadata for analytics catalog entries (BL-212a).
 */
public record AnalyticsCatalogParameter(
        String name,
        String type,
        boolean required,
        String description,
        String defaultValue
) {
}
