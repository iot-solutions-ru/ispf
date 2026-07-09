package com.ispf.analytics.engine;

import java.time.Instant;

/**
 * Optional evaluation context for backfill and point-in-time runs (BL-204).
 */
public record AnalyticsEvaluationOptions(Instant asOf) {

    public static AnalyticsEvaluationOptions now() {
        return new AnalyticsEvaluationOptions(Instant.now());
    }

    public Instant asOfOrNow() {
        return asOf != null ? asOf : Instant.now();
    }
}
