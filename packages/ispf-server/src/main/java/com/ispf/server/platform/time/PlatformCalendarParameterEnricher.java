package com.ispf.server.platform.time;

import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Resolves calendar-relative report/history parameters into UTC {@code from}/{@code to} bounds.
 */
@Service
public class PlatformCalendarParameterEnricher {

    private final PlatformCalendarRangeService calendarRangeService;

    public PlatformCalendarParameterEnricher(PlatformCalendarRangeService calendarRangeService) {
        this.calendarRangeService = calendarRangeService;
    }

    public Map<String, Object> enrich(Map<String, Object> parameters) {
        if (parameters == null || parameters.isEmpty()) {
            return parameters != null ? parameters : Map.of();
        }
        Object calendarRange = parameters.get("calendarRange");
        if (calendarRange == null || calendarRange.toString().isBlank()) {
            return parameters;
        }
        String timeZone = firstNonBlank(parameters, "reportTimeZone", "timeZone");
        PlatformCalendarRangeService.InstantRange range =
                calendarRangeService.resolve(calendarRange.toString(), timeZone);
        Map<String, Object> enriched = new LinkedHashMap<>(parameters);
        putIfAbsent(enriched, "from", range.from().toString());
        putIfAbsent(enriched, "to", range.to().toString());
        putIfAbsent(enriched, "fromTs", range.from().toString());
        putIfAbsent(enriched, "toTs", range.to().toString());
        return enriched;
    }

    private static String firstNonBlank(Map<String, Object> parameters, String... keys) {
        for (String key : keys) {
            Object value = parameters.get(key);
            if (value != null && !value.toString().isBlank()) {
                return value.toString();
            }
        }
        return null;
    }

    private static void putIfAbsent(Map<String, Object> target, String key, String value) {
        Object existing = target.get(key);
        if (existing == null || existing.toString().isBlank()) {
            target.put(key, value);
        }
    }
}
