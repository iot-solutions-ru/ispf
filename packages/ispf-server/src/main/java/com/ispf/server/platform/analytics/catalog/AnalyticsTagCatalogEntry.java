package com.ispf.server.platform.analytics.catalog;

import com.ispf.analytics.engine.AnalyticsSourceRef;

import java.time.Instant;
import java.util.List;

/**
 * Deployed analytics tag catalog row for Explorer and impact analysis (BL-209).
 */
public record AnalyticsTagCatalogEntry(
        String path,
        String displayName,
        String helper,
        String expression,
        String outputVariable,
        List<AnalyticsSourceRef> sources,
        List<String> upstreamTagPaths,
        List<String> downstreamTagPaths,
        String windowBucket,
        List<String> rollupBuckets,
        long periodicMs,
        boolean enabled,
        String qualityStatus,
        String lastEvalStatus,
        Instant lastEvalAt,
        Instant lastTickAt,
        AnalyticsTagLineageGraph lineage
) {
}
