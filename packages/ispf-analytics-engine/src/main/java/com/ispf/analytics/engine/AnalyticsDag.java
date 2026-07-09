package com.ispf.analytics.engine;

import java.util.List;

/**
 * Topologically ordered analytics tags ready for evaluation (BL-203).
 */
public record AnalyticsDag(List<AnalyticsTagDefinition> orderedTags) {

    public AnalyticsDag {
        orderedTags = List.copyOf(orderedTags);
    }
}
