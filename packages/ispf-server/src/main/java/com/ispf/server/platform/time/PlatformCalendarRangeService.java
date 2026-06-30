package com.ispf.server.platform.time;

import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.temporal.ChronoUnit;
import java.util.Locale;

/**
 * Resolves calendar-relative history windows (today, yesterday) in a user or device IANA zone.
 */
@Service
public class PlatformCalendarRangeService {

    public record InstantRange(Instant from, Instant to) {
    }

    public InstantRange resolve(String calendarRange, String timeZone) {
        if (calendarRange == null || calendarRange.isBlank()) {
            throw new IllegalArgumentException("calendarRange is required");
        }
        String normalized = calendarRange.trim().toLowerCase(Locale.ROOT);
        ZoneId zone = ZoneId.of(PlatformTimeZones.normalizeOrDefault(timeZone));
        ZonedDateTime now = ZonedDateTime.now(zone);
        return switch (normalized) {
            case "today" -> new InstantRange(
                    now.truncatedTo(ChronoUnit.DAYS).toInstant(),
                    now.toInstant()
            );
            case "yesterday" -> {
                ZonedDateTime start = now.minusDays(1).truncatedTo(ChronoUnit.DAYS);
                ZonedDateTime end = start.plusDays(1).minusNanos(1);
                yield new InstantRange(start.toInstant(), end.toInstant());
            }
            default -> throw new IllegalArgumentException("Unsupported calendarRange: " + calendarRange);
        };
    }
}
