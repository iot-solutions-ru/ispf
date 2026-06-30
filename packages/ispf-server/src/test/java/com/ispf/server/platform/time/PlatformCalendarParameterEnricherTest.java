package com.ispf.server.platform.time;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.temporal.ChronoUnit;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class PlatformCalendarParameterEnricherTest {

    private final PlatformCalendarParameterEnricher enricher =
            new PlatformCalendarParameterEnricher(new PlatformCalendarRangeService());

    @Test
    void enrichesTodayBoundsWithoutOverwritingExplicitFrom() {
        Map<String, Object> result = enricher.enrich(Map.of(
                "calendarRange", "today",
                "reportTimeZone", "UTC",
                "from", "2020-01-01T00:00:00Z"
        ));

        assertThat(result.get("from")).isEqualTo("2020-01-01T00:00:00Z");
        Instant todayStart = ZonedDateTime.now(ZoneId.of("UTC")).truncatedTo(ChronoUnit.DAYS).toInstant();
        assertThat(result.get("to")).isNotNull();
        assertThat(result.get("fromTs")).isEqualTo(todayStart.toString());
    }

    @Test
    void enrichesYesterdayUsingTimeZoneAlias() {
        ZoneId zone = ZoneId.of("Europe/Moscow");
        Map<String, Object> result = enricher.enrich(Map.of(
                "calendarRange", "yesterday",
                "timeZone", zone.getId()
        ));

        ZonedDateTime start = ZonedDateTime.now(zone).minusDays(1).truncatedTo(ChronoUnit.DAYS);
        assertThat(result.get("from")).isEqualTo(start.toInstant().toString());
        assertThat(result.get("to")).isEqualTo(start.plusDays(1).minusNanos(1).toInstant().toString());
    }

    @Test
    void leavesParametersUntouchedWithoutCalendarRange() {
        Map<String, Object> input = Map.of("itemCode", "A1");
        assertThat(enricher.enrich(input)).isSameAs(input);
    }
}
