package com.ispf.server.platform.analytics;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Multi-tag analytics query response with aligned buckets (BL-206).
 */
public record AnalyticsQueryResponse(
        String bucket,
        Instant from,
        Instant to,
        String agg,
        List<String> timestamps,
        List<AnalyticsQuerySeries> series,
        long latencyMs,
        UUID frameId,
        String frameType
) {

    public record AnalyticsQuerySeries(
            String id,
            String path,
            String variable,
            String field,
            String dataSource,
            List<Double> values
    ) {
    }
}
