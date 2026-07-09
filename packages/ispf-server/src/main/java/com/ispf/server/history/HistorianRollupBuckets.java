package com.ispf.server.history;

import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;

/**
 * Parses rollup bucket lists for OLAP materialization (BL-205).
 */
public final class HistorianRollupBuckets {

    public static final String DEFAULT_SPEC = "5m,1h,8h";

    private HistorianRollupBuckets() {
    }

    public static List<String> parse(String spec) {
        if (spec == null || spec.isBlank()) {
            return defaultBuckets();
        }
        LinkedHashSet<String> buckets = new LinkedHashSet<>();
        for (String part : spec.split(",")) {
            String trimmed = part.trim().toLowerCase(Locale.ROOT);
            if (!trimmed.isEmpty()) {
                VariableHistoryService.parseBucket(trimmed);
                buckets.add(trimmed);
            }
        }
        if (buckets.isEmpty()) {
            return defaultBuckets();
        }
        return List.copyOf(buckets);
    }

    public static List<String> defaultForWindow(String windowBucket) {
        LinkedHashSet<String> buckets = new LinkedHashSet<>();
        if (windowBucket != null && !windowBucket.isBlank()) {
            buckets.add(windowBucket.trim().toLowerCase(Locale.ROOT));
        }
        buckets.addAll(defaultBuckets());
        return List.copyOf(buckets);
    }

    public static List<String> defaultBuckets() {
        return parse(DEFAULT_SPEC);
    }

    public static String formatBucket(Duration bucket) {
        return VariableHistoryService.formatBucket(bucket);
    }

    public static String join(List<String> buckets) {
        return String.join(",", buckets);
    }

    public static List<Duration> toDurations(List<String> buckets) {
        List<Duration> result = new ArrayList<>(buckets.size());
        for (String bucket : buckets) {
            result.add(VariableHistoryService.parseBucket(bucket));
        }
        return result;
    }
}
