package com.ispf.server.history;

import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

class HistorianSampledAtResolverTest {

    @Test
    void spreadsCollidingSamplesByOneMillisecondPerSeries() {
        HistorianSampledAtResolver resolver = new HistorianSampledAtResolver();
        Instant wall = Instant.parse("2026-07-12T07:00:00.123Z");
        String key = "root.dev.sensor|temperature|raw";

        Instant first = resolver.resolve(true, key, null, wall);
        Instant second = resolver.resolve(true, key, null, wall);
        Instant third = resolver.resolve(true, key, null, wall);

        assertThat(first.toEpochMilli()).isEqualTo(wall.toEpochMilli());
        assertThat(second.toEpochMilli()).isEqualTo(wall.toEpochMilli() + 1);
        assertThat(third.toEpochMilli()).isEqualTo(wall.toEpochMilli() + 2);
    }

    @Test
    void disabledUsesWallClock() {
        HistorianSampledAtResolver resolver = new HistorianSampledAtResolver();
        Instant wall = Instant.parse("2026-07-12T07:00:00.123Z");

        assertThat(resolver.resolve(false, "a|b|c", null, wall)).isEqualTo(wall);
    }
}
