package com.ispf.server.alert;

import com.ispf.core.object.PlatformObject;

import java.time.Instant;
import java.util.Optional;

/**
 * Ephemeral alert-rule evaluation state (edge detection, sustain timers, rate limits).
 * Hot path keeps this in RAM; {@link AlertRuleRuntimeFlusher} persists dirty entries periodically.
 */
public record AlertRuleRuntimeState(
        Boolean lastConditionMet,
        Double lastWatchValue,
        Instant lastFiredAt,
        Instant conditionTrueSince
) {
    public static AlertRuleRuntimeState empty() {
        return new AlertRuleRuntimeState(null, null, null, null);
    }

    public static AlertRuleRuntimeState fromNode(PlatformObject node) {
        return new AlertRuleRuntimeState(
                readBoolean(node, "lastConditionMet").orElse(null),
                readString(node, "lastWatchValue")
                        .filter(value -> !value.isBlank())
                        .map(Double::parseDouble)
                        .orElse(null),
                parseInstant(readString(node, "lastFiredAt").orElse("")),
                parseInstant(readString(node, "conditionTrueSince").orElse(""))
        );
    }

    private static Optional<String> readString(PlatformObject node, String variable) {
        return node.getVariable(variable)
                .flatMap(v -> v.value())
                .map(record -> record.firstRow().get("value"))
                .map(Object::toString);
    }

    private static Optional<Boolean> readBoolean(PlatformObject node, String variable) {
        return node.getVariable(variable)
                .flatMap(v -> v.value())
                .map(record -> record.firstRow().get("value"))
                .map(value -> value instanceof Boolean bool ? bool : Boolean.parseBoolean(String.valueOf(value)));
    }

    private static Instant parseInstant(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        try {
            return Instant.parse(raw);
        } catch (Exception ex) {
            return null;
        }
    }
}
