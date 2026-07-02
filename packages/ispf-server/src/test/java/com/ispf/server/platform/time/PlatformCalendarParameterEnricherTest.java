package com.ispf.server.platform.time;

import com.ispf.server.security.PlatformUserService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.temporal.ChronoUnit;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class PlatformCalendarParameterEnricherTest {

    @Mock
    private PlatformUserService userService;

    private PlatformCalendarParameterEnricher enricher;

    @org.junit.jupiter.api.BeforeEach
    void setUp() {
        enricher = new PlatformCalendarParameterEnricher(new PlatformCalendarRangeService(), userService);
        when(userService.findTimeZone(anyString())).thenReturn(java.util.Optional.of("UTC"));
    }

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
    void defaultsReportTimeZoneFromUserHintWhenMissing() {
        when(userService.findTimeZone("alice")).thenReturn(java.util.Optional.of("Europe/Moscow"));
        Map<String, Object> result = enricher.enrich(Map.of("calendarRange", "today"), "alice");
        assertThat(result.get("reportTimeZone")).isEqualTo("Europe/Moscow");
        assertThat(result.get("from")).isNotNull();
        assertThat(result.get("to")).isNotNull();
    }

    @Test
    void leavesParametersUntouchedWithoutCalendarRange() {
        Map<String, Object> input = Map.of("itemCode", "A1");
        assertThat(enricher.enrich(input)).isSameAs(input);
    }
}
