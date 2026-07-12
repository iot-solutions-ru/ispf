package com.ispf.server.query;

import com.ispf.core.ref.PlatformRef;
import com.ispf.core.ref.PlatformRefKind;
import com.ispf.server.history.VariableHistoryService;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Locale;

/**
 * Resolves OQ field historian aggregates ({@code historian.fn} + {@code historian.window}) per row.
 */
@Component
public class ObjectQueryHistorianColumnResolver {

    private final ObjectProvider<VariableHistoryService> variableHistoryService;

    public ObjectQueryHistorianColumnResolver(ObjectProvider<VariableHistoryService> variableHistoryService) {
        this.variableHistoryService = variableHistoryService;
    }

    public Object resolve(String fn, String window, PlatformRef ref) {
        if (fn == null || fn.isBlank() || ref == null || ref.kind() != PlatformRefKind.VARIABLE) {
            return null;
        }
        VariableHistoryService history = variableHistoryService.getIfAvailable();
        if (history == null) {
            return null;
        }
        String objectPath = ref.object();
        String variableName = ref.name();
        String fieldName = ref.field();
        Duration lookback = parseWindow(window);
        Instant to = Instant.now();
        Instant from = to.minus(lookback);
        String aggregate = fn.trim().toLowerCase(Locale.ROOT);
        if ("latest".equals(aggregate) || "last".equals(aggregate)) {
            return history.query(objectPath, variableName, fieldName, from, to, 1)
                    .samples()
                    .stream()
                    .findFirst()
                    .map(sample -> sample.value() != null ? sample.value() : sample.text())
                    .orElse(null);
        }
        List<VariableHistoryService.VariableHistorySample> samples = history
                .query(objectPath, variableName, fieldName, from, to, 5_000)
                .samples();
        if (samples.isEmpty()) {
            return null;
        }
        return switch (aggregate) {
            case "count" -> samples.size();
            case "sum" -> samples.stream().mapToDouble(this::sampleNumeric).sum();
            case "avg" -> samples.stream().mapToDouble(this::sampleNumeric).average().orElse(0);
            case "min" -> samples.stream().mapToDouble(this::sampleNumeric).min().orElse(0);
            case "max" -> samples.stream().mapToDouble(this::sampleNumeric).max().orElse(0);
            case "first" -> sampleValue(samples.getFirst());
            default -> null;
        };
    }

    private double sampleNumeric(VariableHistoryService.VariableHistorySample sample) {
        if (sample.value() != null) {
            return sample.value();
        }
        if (sample.text() != null) {
            try {
                return Double.parseDouble(sample.text());
            } catch (NumberFormatException ignored) {
                return 0;
            }
        }
        return 0;
    }

    private static Object sampleValue(VariableHistoryService.VariableHistorySample sample) {
        if (sample.value() != null) {
            return sample.value();
        }
        return sample.text();
    }

    static Duration parseWindow(String window) {
        if (window == null || window.isBlank()) {
            return Duration.ofMinutes(15);
        }
        try {
            return VariableHistoryService.parseBucket(window.trim());
        } catch (RuntimeException ex) {
            return Duration.ofMinutes(15);
        }
    }
}
