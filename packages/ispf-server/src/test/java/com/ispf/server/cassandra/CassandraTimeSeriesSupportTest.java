package com.ispf.server.cassandra;

import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertEquals;

class CassandraTimeSeriesSupportTest {

    @Test
    void ttlSecondsFromRetentionDays() {
        assertEquals(7_776_000, CassandraTimeSeriesSupport.ttlSeconds(90));
        assertEquals(0, CassandraTimeSeriesSupport.ttlSeconds(0));
    }

    @Test
    void monthBucketUsesUtc() {
        assertEquals(
                "2025-06",
                CassandraTimeSeriesSupport.monthBucket(Instant.parse("2025-06-25T12:00:00Z"))
        );
    }

    @Test
    void recentMonthBucketsWalksBackwards() {
        Instant anchor = Instant.parse("2025-06-25T12:00:00Z");
        assertEquals(
                java.util.List.of("2025-06", "2025-05", "2025-04"),
                CassandraTimeSeriesSupport.recentMonthBuckets(anchor, 3)
        );
    }

    @Test
    void variableSamplePartitionKeyIsStable() {
        assertEquals(
                "root.dev.sensor\0temperature\0raw",
                CassandraTimeSeriesSupport.variableSamplePartitionKey("root.dev.sensor", "temperature", "raw")
        );
    }

    @Test
    void chunkSplitsLists() {
        assertEquals(java.util.List.of(), CassandraTimeSeriesSupport.chunk(java.util.List.of(), 10));
        assertEquals(
                java.util.List.of(java.util.List.of(1, 2), java.util.List.of(3)),
                CassandraTimeSeriesSupport.chunk(java.util.List.of(1, 2, 3), 2)
        );
    }
}
