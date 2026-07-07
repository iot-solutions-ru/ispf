package com.ispf.core.ml;

import java.time.Instant;
import java.util.Map;

/**
 * Result of an anomaly scoring pass (BL-175).
 */
public record AnomalyDetectionResult(
        String modelId,
        double score,
        boolean anomaly,
        String label,
        Instant scoredAt,
        Map<String, Object> details
) {
}
