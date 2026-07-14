package com.ispf.server.history;

import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class ClickHouseDateTimesTest {

    @Test
    void parseWholeSecondBucketStartFromAggregates() {
        assertEquals(
                Instant.parse("2026-07-14T09:00:00Z"),
                ClickHouseDateTimes.parse("2026-07-14 09:00:00")
        );
    }

    @Test
    void parseMillisBucketStart() {
        assertEquals(
                Instant.parse("2026-07-14T09:00:00.123Z"),
                ClickHouseDateTimes.parse("2026-07-14 09:00:00.123")
        );
    }

    @Test
    void parseOrNullBlank() {
        assertNull(ClickHouseDateTimes.parseOrNull(""));
        assertNull(ClickHouseDateTimes.parseOrNull(null));
    }
}
