package com.ispf.server.platform.analytics.catalog;

import java.util.List;

/**
 * Unified analytics function catalog row for API consumers (ADR-0042 / BL-212a).
 */
public record AnalyticsCatalogEntry(
        String id,
        String displayName,
        String tier,
        List<String> kinds,
        String syntax,
        List<AnalyticsCatalogParameter> parameters,
        String description,
        List<String> examples,
        List<String> tags,
        String pack,
        String docAnchor
) {
}
