package com.ispf.server.platform.analytics.catalog;

/**
 * Node in an analytics tag lineage graph.
 *
 * @param kind {@code tag} or {@code source}
 */
public record AnalyticsTagLineageNode(
        String id,
        String kind,
        String label,
        String path,
        String variable
) {
}
