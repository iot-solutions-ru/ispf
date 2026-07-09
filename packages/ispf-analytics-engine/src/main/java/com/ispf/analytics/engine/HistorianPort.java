package com.ispf.analytics.engine;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * Historian read port for analytics evaluators (BL-203).
 */
public interface HistorianPort {

    List<HistorianBucket> aggregate(
            String objectPath,
            String variableName,
            String fieldName,
            Instant from,
            Instant to,
            String windowBucket,
            int maxBuckets
    );

    List<HistorianSample> query(
            String objectPath,
            String variableName,
            String fieldName,
            Instant from,
            Instant to,
            int limit
    );

    record HistorianBucket(Instant ts, Double avg, Double min, Double max, int count) {
    }

    record HistorianSample(Instant ts, Double value, String text) {
    }
}
