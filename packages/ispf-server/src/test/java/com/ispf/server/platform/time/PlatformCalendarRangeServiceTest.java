package com.ispf.server.platform.time;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.temporal.ChronoUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PlatformCalendarRangeServiceTest {

    private final PlatformCalendarRangeService service = new PlatformCalendarRangeService();

    @Test
    void todayStartsAtMidnightInZone() {
        ZoneId zone = ZoneId.of("Europe/Moscow");
        PlatformCalendarRangeService.InstantRange range = service.resolve("today", zone.getId());

        ZonedDateTime now = ZonedDateTime.now(zone);
        Instant expectedStart = now.truncatedTo(ChronoUnit.DAYS).toInstant();
        assertThat(range.from()).isEqualTo(expectedStart);
        assertThat(range.to()).isBeforeOrEqualTo(Instant.now().plusSeconds(1));
        assertThat(range.to()).isAfterOrEqualTo(expectedStart);
    }

    @Test
    void yesterdayCoversFullLocalDay() {
        ZoneId zone = ZoneId.of("UTC");
        PlatformCalendarRangeService.InstantRange range = service.resolve("yesterday", zone.getId());

        ZonedDateTime start = ZonedDateTime.now(zone).minusDays(1).truncatedTo(ChronoUnit.DAYS);
        assertThat(range.from()).isEqualTo(start.toInstant());
        assertThat(range.to()).isEqualTo(start.plusDays(1).minusNanos(1).toInstant());
    }

    @Test
    void rejectsUnknownRange() {
        assertThatThrownBy(() -> service.resolve("last-week", "UTC"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Unsupported calendarRange");
    }
}
