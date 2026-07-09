package com.ispf.server.platform.analytics;

import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * Multi-tag analytics query request (BL-206).
 */
public record AnalyticsQueryRequest(
        List<AnalyticsQueryTag> tags,
        Instant from,
        Instant to,
        String bucket,
        String agg,
        Integer maxBuckets,
        /** Optional analytics event frame — overrides from/to with frame boundaries (BL-208). */
        UUID frameId
) {

    public static final String DEFAULT_AGG = "avg";

    public AnalyticsQueryRequest {
        Objects.requireNonNull(tags, "tags");
        Objects.requireNonNull(from, "from");
        Objects.requireNonNull(to, "to");
        if (bucket == null || bucket.isBlank()) {
            throw new IllegalArgumentException("bucket is required");
        }
        if (agg == null || agg.isBlank()) {
            agg = DEFAULT_AGG;
        }
        tags = List.copyOf(tags);
    }

    public record AnalyticsQueryTag(
            String path,
            String variable,
            String field,
            String label
    ) {
        public AnalyticsQueryTag {
            Objects.requireNonNull(path, "path");
            Objects.requireNonNull(variable, "variable");
            if (field == null || field.isBlank()) {
                field = "value";
            }
        }

        public String seriesId() {
            if (label != null && !label.isBlank()) {
                return label;
            }
            return path + "." + variable;
        }
    }
}
