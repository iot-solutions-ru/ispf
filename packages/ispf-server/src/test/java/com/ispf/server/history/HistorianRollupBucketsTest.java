package com.ispf.server.history;

import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class HistorianRollupBucketsTest {

    @Test
    void parsesCommaSeparatedBuckets() {
        assertThat(HistorianRollupBuckets.parse("5m,1h,8h"))
                .containsExactly("5m", "1h", "8h");
    }

    @Test
    void defaultForWindowIncludesWindowAndStandards() {
        List<String> buckets = HistorianRollupBuckets.defaultForWindow("15m");
        assertThat(buckets).contains("15m", "5m", "1h", "8h");
    }

    @Test
    void rejectsUnsupportedBucket() {
        assertThatThrownBy(() -> HistorianRollupBuckets.parse("2w"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void formatsDurationBackToSpec() {
        assertThat(HistorianRollupBuckets.formatBucket(Duration.ofHours(8))).isEqualTo("8h");
    }
}
