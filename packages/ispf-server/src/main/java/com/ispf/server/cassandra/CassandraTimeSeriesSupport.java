package com.ispf.server.cassandra;

import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

public final class CassandraTimeSeriesSupport {

    private static final DateTimeFormatter MONTH_BUCKET = DateTimeFormatter.ofPattern("yyyy-MM").withZone(ZoneOffset.UTC);

    private CassandraTimeSeriesSupport() {
    }

    public static int ttlSeconds(int retentionDays) {
        return retentionDays > 0 ? retentionDays * 86_400 : 0;
    }

    public static String monthBucket(Instant instant) {
        return MONTH_BUCKET.format(instant);
    }

    public static List<String> recentMonthBuckets(Instant anchor, int count) {
        List<String> buckets = new ArrayList<>(count);
        Instant cursor = anchor;
        for (int i = 0; i < count; i++) {
            buckets.add(monthBucket(cursor));
            cursor = cursor.atZone(ZoneOffset.UTC).minusMonths(1).toInstant();
        }
        return buckets;
    }

    /** CQL partition key for {@code variable_samples ((object_path, variable_name, field_name), ...)}. */
    public static String variableSamplePartitionKey(String objectPath, String variableName, String fieldName) {
        return objectPath + "\0" + variableName + "\0" + fieldName;
    }

    public static <T> List<List<T>> chunk(List<T> items, int maxChunkSize) {
        if (items.isEmpty()) {
            return List.of();
        }
        int chunkSize = Math.max(1, maxChunkSize);
        List<List<T>> chunks = new ArrayList<>((items.size() + chunkSize - 1) / chunkSize);
        for (int i = 0; i < items.size(); i += chunkSize) {
            chunks.add(items.subList(i, Math.min(i + chunkSize, items.size())));
        }
        return chunks;
    }
}
