package com.ispf.server.schedule;

import org.springframework.scheduling.support.CronExpression;

import java.time.DateTimeException;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Due-check for platform SCHEDULE objects: intervalMs by default, optional cronExpression + timeZone.
 * Cron forms: {@code every:Nm} / {@code every:N} (minutes), or Spring 6-field cron (seconds first).
 * Five-field (minute-first) expressions are accepted and prefixed with {@code 0 }.
 */
public final class ScheduleDueChecker {

    private static final Pattern EVERY_MINUTES = Pattern.compile(
            "^every:\\s*(\\d+)\\s*m?$",
            Pattern.CASE_INSENSITIVE
    );

    private ScheduleDueChecker() {
    }

    public static boolean isDue(
            Instant now,
            Instant lastTickAt,
            long intervalMs,
            String cronExpression,
            String timeZone
    ) {
        if (now == null) {
            return false;
        }
        String cron = cronExpression == null ? "" : cronExpression.trim();
        if (cron.isBlank()) {
            if (lastTickAt == null) {
                return true;
            }
            long interval = Math.max(1L, intervalMs);
            return !lastTickAt.plusMillis(interval).isAfter(now);
        }
        Matcher every = EVERY_MINUTES.matcher(cron);
        if (every.matches()) {
            long minutes = Long.parseLong(every.group(1));
            long interval = Math.max(1L, minutes) * 60_000L;
            if (lastTickAt == null) {
                return true;
            }
            return !lastTickAt.plusMillis(interval).isAfter(now);
        }
        ZoneId zone = resolveZone(timeZone);
        CronExpression parsed = parseCron(cron);
        ZonedDateTime cursor = (lastTickAt != null ? lastTickAt : now.minus(Duration.ofDays(1))).atZone(zone);
        ZonedDateTime next = parsed.next(cursor);
        if (next == null) {
            return false;
        }
        return !next.toInstant().isAfter(now);
    }

    static ZoneId resolveZone(String timeZone) {
        if (timeZone == null || timeZone.isBlank()) {
            return ZoneId.of("UTC");
        }
        try {
            return ZoneId.of(timeZone.trim());
        } catch (DateTimeException ex) {
            throw new IllegalArgumentException("Invalid schedule timeZone: " + timeZone, ex);
        }
    }

    static CronExpression parseCron(String raw) {
        String expr = raw.trim();
        String[] parts = expr.split("\\s+");
        if (parts.length == 5) {
            expr = "0 " + expr;
        }
        try {
            return CronExpression.parse(expr);
        } catch (IllegalArgumentException ex) {
            throw new IllegalArgumentException(
                    "Invalid schedule cronExpression (use every:Nm or 5/6-field cron): " + raw,
                    ex
            );
        }
    }

    public static String normalizeCronOrBlank(String cronExpression) {
        if (cronExpression == null || cronExpression.isBlank()) {
            return "";
        }
        String cron = cronExpression.trim();
        if (EVERY_MINUTES.matcher(cron).matches()) {
            return cron.toLowerCase(Locale.ROOT).replace(" ", "");
        }
        parseCron(cron);
        return cron;
    }
}
