package com.ispf.server.platform.analytics.catalog;

/**
 * Directed edge in an analytics tag lineage graph ({@code upstream → tag}).
 */
public record AnalyticsTagLineageEdge(
        String from,
        String to,
        String relation
) {
}
