package com.ispf.server.workflow;

import org.springframework.scheduling.support.CronExpression;

import java.time.DateTimeException;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Due-check for a workflow {@code cronExpression}. Same forms as schedule objects:
 * {@code every:Nm} and Spring 5/6-field cron. Blank is not a schedule.
 */
final class WorkflowCronDue {

    private static final Pattern EVERY_MINUTES = Pattern.compile(
            "^every:\\s*(\\d+)\\s*m?$",
            Pattern.CASE_INSENSITIVE
    );

    private WorkflowCronDue() {
    }

    static boolean isDue(Instant now, Instant lastRunAt, String cronExpression) {
        if (now == null) {
            return false;
        }
        String cron = cronExpression == null ? "" : cronExpression.trim();
        if (cron.isBlank()) {
            return false;
        }
        Matcher every = EVERY_MINUTES.matcher(cron);
        if (every.matches()) {
            long minutes = Long.parseLong(every.group(1));
            long interval = Math.max(1L, minutes) * 60_000L;
            if (lastRunAt == null) {
                return true;
            }
            return !lastRunAt.plusMillis(interval).isAfter(now);
        }
        CronExpression parsed = parseCron(cron);
        ZonedDateTime cursor = (lastRunAt != null ? lastRunAt : now.minus(Duration.ofDays(1))).atZone(ZoneId.of("UTC"));
        ZonedDateTime next = parsed.next(cursor);
        if (next == null) {
            return false;
        }
        return !next.toInstant().isAfter(now);
    }

    private static CronExpression parseCron(String raw) {
        String expr = raw.trim();
        String[] parts = expr.split("\\s+");
        if (parts.length == 5) {
            expr = "0 " + expr;
        }
        try {
            return CronExpression.parse(expr);
        } catch (IllegalArgumentException | DateTimeException ex) {
            throw new IllegalArgumentException(
                    "Invalid workflow cronExpression (use every:Nm or 5/6-field cron): " + raw,
                    ex
            );
        }
    }
}
