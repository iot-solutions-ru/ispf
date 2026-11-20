package com.ispf.server.history;

import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

class JdbcMssqlHistorianRollupMaterializerTest {

    @Test
    void floorToBucketAlignsEpochSeconds() {
        Instant t = Instant.parse("2026-02-01T12:34:56Z");
        assertThat(JdbcMssqlHistorianRollupMaterializer.floorToBucket(t, 3_600))
                .isEqualTo(Instant.parse("2026-02-01T12:00:00Z"));
        assertThat(JdbcMssqlHistorianRollupMaterializer.floorToBucket(t, 86_400))
                .isEqualTo(Instant.parse("2026-02-01T00:00:00Z"));
    }

    @Test
    void ceilToBucketRoundsUpPartialBuckets() {
        Instant t = Instant.parse("2026-02-01T12:34:56Z");
        assertThat(JdbcMssqlHistorianRollupMaterializer.ceilToBucket(t, 3_600))
                .isEqualTo(Instant.parse("2026-02-01T13:00:00Z"));
        assertThat(JdbcMssqlHistorianRollupMaterializer.ceilToBucket(
                Instant.parse("2026-02-01T12:00:00Z"), 3_600))
                .isEqualTo(Instant.parse("2026-02-01T12:00:00Z"));
        assertThat(JdbcMssqlHistorianRollupMaterializer.ceilToBucket(
                Instant.parse("2026-02-01T12:00:00.500Z"), 3_600))
                .isEqualTo(Instant.parse("2026-02-01T13:00:00Z"));
    }
}
