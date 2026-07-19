package com.ispf.server.eventfilter;

import com.ispf.core.model.DataRecord;
import com.ispf.core.object.EventLevel;
import com.ispf.core.object.ObjectEvent;
import com.ispf.expression.ExpressionEngine;
import com.ispf.expression.ExpressionException;
import com.ispf.server.eventfilter.EventFilterObjectService.EventFilterDefinition;
import com.ispf.server.query.ObjectPathPattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * Applies reusable event-filter definitions to journal events (BL-174).
 */
@Component
public class EventFilterMatcher {

    private static final Logger log = LoggerFactory.getLogger(EventFilterMatcher.class);

    private final ExpressionEngine expressionEngine;

    public EventFilterMatcher(ExpressionEngine expressionEngine) {
        this.expressionEngine = expressionEngine;
    }

    public boolean matches(EventFilterDefinition filter, ObjectEvent event) {
        if (filter == null || event == null) {
            return false;
        }
        if (!filter.enabled()) {
            return false;
        }
        if (!matchesNamePattern(event.eventName(), filter.eventNamePattern())) {
            return false;
        }
        if (!ObjectPathPattern.matches(event.objectPath(), filter.sourceObjectPathPattern())) {
            return false;
        }
        int severity = severityOf(event.level());
        if (severity < filter.minSeverity() || severity > filter.maxSeverity()) {
            return false;
        }
        if (filter.timeWindowMs() > 0) {
            Instant cutoff = Instant.now().minusMillis(filter.timeWindowMs());
            if (event.timestamp() == null || event.timestamp().isBefore(cutoff)) {
                return false;
            }
        }
        return matchesExpression(filter.filterExpression(), event);
    }

    private boolean matchesExpression(String expression, ObjectEvent event) {
        if (expression == null || expression.isBlank()) {
            return true;
        }
        try {
            Map<String, Object> payload = new HashMap<>();
            DataRecord record = event.payload();
            if (record != null && record.rowCount() > 0) {
                for (Map.Entry<String, Object> entry : record.firstRow().entrySet()) {
                    Object value = entry.getValue();
                    if (value instanceof Number number) {
                        payload.put(entry.getKey(), number.doubleValue());
                    } else {
                        payload.put(entry.getKey(), value);
                    }
                }
            }
            // Metadata wins over payload field collisions.
            payload.put("eventName", event.eventName());
            payload.put("objectPath", event.objectPath());
            payload.put("level", event.level() != null ? event.level().name() : "");
            payload.put("severity", (double) severityOf(event.level()));
            payload.put("timestamp", event.timestamp() != null ? event.timestamp().toString() : "");
            Object result = expressionEngine.evaluateWithPayload(expression, payload);
            if (result instanceof Boolean bool) {
                return bool;
            }
            return Boolean.parseBoolean(String.valueOf(result));
        } catch (ExpressionException ex) {
            log.warn("Event filter expression failed: {}", ex.getMessage());
            return false;
        }
    }

    static boolean matchesNamePattern(String eventName, String pattern) {
        if (pattern == null || pattern.isBlank() || "*".equals(pattern.trim())) {
            return true;
        }
        String trimmed = pattern.trim();
        if (!trimmed.contains("*") && !trimmed.contains("?")) {
            return trimmed.equals(eventName);
        }
        String regex = "^" + trimmed
                .replace(".", "\\.")
                .replace("?", ".")
                .replace("*", ".*") + "$";
        return Pattern.compile(regex).matcher(eventName == null ? "" : eventName).matches();
    }

    /**
     * Maps {@link EventLevel} onto the 0–100 severity scale used by filter definitions.
     */
    static int severityOf(EventLevel level) {
        if (level == null) {
            return 0;
        }
        return switch (level) {
            case DEBUG -> 10;
            case INFO -> 30;
            case WARNING -> 50;
            case ERROR -> 70;
            case CRITICAL -> 90;
        };
    }
}
