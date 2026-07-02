package com.ispf.server.platform.time;

import com.ispf.server.security.PlatformUserService;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

/**
 * Resolves calendar-relative report/history parameters into UTC {@code from}/{@code to} bounds.
 */
@Service
public class PlatformCalendarParameterEnricher {

    private final PlatformCalendarRangeService calendarRangeService;
    private final PlatformUserService userService;

    public PlatformCalendarParameterEnricher(
            PlatformCalendarRangeService calendarRangeService,
            PlatformUserService userService
    ) {
        this.calendarRangeService = calendarRangeService;
        this.userService = userService;
    }

    public Map<String, Object> enrich(Map<String, Object> parameters) {
        return enrich(parameters, null);
    }

    public Map<String, Object> enrich(Map<String, Object> parameters, String usernameHint) {
        if (parameters == null || parameters.isEmpty()) {
            return parameters != null ? parameters : Map.of();
        }
        Object calendarRange = parameters.get("calendarRange");
        if (calendarRange == null || calendarRange.toString().isBlank()) {
            return parameters;
        }
        String timeZone = firstNonBlank(parameters, "reportTimeZone", "timeZone");
        if (timeZone == null || timeZone.isBlank()) {
            timeZone = resolveUserTimeZone(usernameHint).orElse("UTC");
        }
        PlatformCalendarRangeService.InstantRange range =
                calendarRangeService.resolve(calendarRange.toString(), timeZone);
        Map<String, Object> enriched = new LinkedHashMap<>(parameters);
        putIfAbsent(enriched, "from", range.from().toString());
        putIfAbsent(enriched, "to", range.to().toString());
        putIfAbsent(enriched, "fromTs", range.from().toString());
        putIfAbsent(enriched, "toTs", range.to().toString());
        putIfAbsent(enriched, "reportTimeZone", timeZone);
        return enriched;
    }

    private Optional<String> resolveUserTimeZone(String usernameHint) {
        if (usernameHint != null && !usernameHint.isBlank()) {
            Optional<String> fromHint = userService.findTimeZone(usernameHint);
            if (fromHint.isPresent()) {
                return fromHint;
            }
        }
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()) {
            return Optional.empty();
        }
        String name = authentication.getName();
        if (name == null || name.isBlank() || "anonymousUser".equals(name)) {
            return Optional.empty();
        }
        return userService.findTimeZone(name);
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
