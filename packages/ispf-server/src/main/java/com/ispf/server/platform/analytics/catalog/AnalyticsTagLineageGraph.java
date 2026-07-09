package com.ispf.server.platform.analytics.catalog;

import java.util.List;

/**
 * Lineage graph for analytics tag impact analysis (BL-209).
 */
public record AnalyticsTagLineageGraph(
        List<AnalyticsTagLineageNode> nodes,
        List<AnalyticsTagLineageEdge> edges
) {
    public static AnalyticsTagLineageGraph empty() {
        return new AnalyticsTagLineageGraph(List.of(), List.of());
    }
}
