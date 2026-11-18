package com.ispf.analytics.engine.eval;

import java.time.Duration;
import java.util.Locale;

final class WindowBucketDurations {

    private WindowBucketDurations() {
    }

    static Duration parse(String bucketSpec) {
        if (bucketSpec == null || bucketSpec.isBlank()) {
            throw new IllegalArgumentException("Bucket is required");
        }
        String normalized = bucketSpec.trim().toLowerCase(Locale.ROOT);
        return switch (normalized) {
            case "1m" -> Duration.ofMinutes(1);
            case "5m" -> Duration.ofMinutes(5);
            case "15m" -> Duration.ofMinutes(15);
            case "30m" -> Duration.ofMinutes(30);
            case "1h" -> Duration.ofHours(1);
            case "6h" -> Duration.ofHours(6);
            case "8h" -> Duration.ofHours(8);
            case "1d" -> Duration.ofDays(1);
            default -> throw new IllegalArgumentException("Unsupported bucket: " + bucketSpec);
        };
    }
}
