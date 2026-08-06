package com.ispf.server.schedule;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ScheduleDueCheckerTest {

    @Test
    void intervalDueWhenNeverTicked() {
        Instant now = Instant.parse("2026-08-06T12:00:00Z");
        assertThat(ScheduleDueChecker.isDue(now, null, 60_000, "", "UTC")).isTrue();
    }

    @Test
    void intervalNotDueInsideWindow() {
        Instant now = Instant.parse("2026-08-06T12:00:30Z");
        Instant last = Instant.parse("2026-08-06T12:00:00Z");
        assertThat(ScheduleDueChecker.isDue(now, last, 60_000, null, null)).isFalse();
    }

    @Test
    void everyMinutesActsAsInterval() {
        Instant now = Instant.parse("2026-08-06T12:05:00Z");
        Instant last = Instant.parse("2026-08-06T12:00:00Z");
        assertThat(ScheduleDueChecker.isDue(now, last, 60_000, "every:5m", "UTC")).isTrue();
        assertThat(ScheduleDueChecker.isDue(now, last, 60_000, "every:10m", "UTC")).isFalse();
    }

    @Test
    void fiveFieldCronDailyAtEightUtc() {
        Instant eight = LocalDateTime.of(2026, 8, 6, 8, 0).toInstant(ZoneOffset.UTC);
        Instant before = eight.minusSeconds(30);
        Instant after = eight.plusSeconds(30);
        assertThat(ScheduleDueChecker.isDue(before, eight.minusSeconds(86_400), 60_000, "0 8 * * *", "UTC"))
                .isFalse();
        assertThat(ScheduleDueChecker.isDue(after, eight.minusSeconds(86_400), 60_000, "0 8 * * *", "UTC"))
                .isTrue();
        assertThat(ScheduleDueChecker.isDue(after, eight, 60_000, "0 8 * * *", "UTC")).isFalse();
    }

    @Test
    void rejectsBadTimezone() {
        assertThatThrownBy(() -> ScheduleDueChecker.isDue(
                Instant.now(), null, 1000, "0 8 * * *", "Not/AZone"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("timeZone");
    }
}
