package com.ispf.server.history;

import com.ispf.server.persistence.entity.VariableSampleEntity;

import java.time.Instant;
import java.util.Locale;

/** Append-only historian row (write path; id assigned by DB). */
public record VariableHistoryWriteRecord(
        String objectPath,
        String variableName,
        String fieldName,
        Instant sampledAt,
        Instant observedAt,
        Double valueDouble,
        String valueText,
        String quality
) {

    public VariableHistoryWriteRecord {
        if (observedAt == null) {
            observedAt = sampledAt;
        }
        quality = normalizeQuality(quality);
    }

    /**
     * Canonical {@code GOOD} / {@code UNCERTAIN} / {@code BAD}, or null.
     * Blank and unrecognized tokens stay null (unknown / pre-v2), not GOOD.
     */
    public static String normalizeQuality(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        String token = raw.trim().toUpperCase(Locale.ROOT);
        return switch (token) {
            case "GOOD", "UNCERTAIN", "BAD" -> token;
            case "UNCERT" -> "UNCERTAIN";
            default -> null;
        };
    }

    public static VariableHistoryWriteRecord ingested(
            String objectPath,
            String variableName,
            String fieldName,
            Instant ingestedAt,
            Double valueDouble,
            String valueText
    ) {
        return ingested(objectPath, variableName, fieldName, ingestedAt, valueDouble, valueText, null);
    }

    public static VariableHistoryWriteRecord ingested(
            String objectPath,
            String variableName,
            String fieldName,
            Instant ingestedAt,
            Double valueDouble,
            String valueText,
            String quality
    ) {
        return new VariableHistoryWriteRecord(
                objectPath,
                variableName,
                fieldName,
                ingestedAt,
                ingestedAt,
                valueDouble,
                valueText,
                quality
        );
    }

    public static VariableHistoryWriteRecord fromEntity(VariableSampleEntity entity) {
        return new VariableHistoryWriteRecord(
                entity.getObjectPath(),
                entity.getVariableName(),
                entity.getFieldName(),
                entity.getSampledAt(),
                entity.getObservedAt(),
                entity.getValueDouble(),
                entity.getValueText(),
                entity.getQuality()
        );
    }
}
