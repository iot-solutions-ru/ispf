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

    @Test
    void appendWriteMatchesTheInsertFormatter() {
        long[] samples = {
                0L,
                1L,
                999L,
                1_700_000_000_123L,
                Instant.parse("2024-02-29T23:59:59.001Z").toEpochMilli(),
                Instant.parse("1969-12-31T23:59:59.999Z").toEpochMilli(),
                Instant.parse("2099-12-31T00:00:00Z").toEpochMilli(),
                Instant.parse("-0001-01-01T00:00:00Z").toEpochMilli(),
                Instant.parse("+10000-01-01T00:00:00Z").toEpochMilli()
        };
        for (long epochMilli : samples) {
            Instant instant = Instant.ofEpochMilli(epochMilli);
            StringBuilder body = new StringBuilder();
            ClickHouseDateTimes.appendWrite(body, instant);
            assertEquals(ClickHouseDateTimes.WRITE.format(instant), body.toString(), instant.toString());
        }
    }
}
