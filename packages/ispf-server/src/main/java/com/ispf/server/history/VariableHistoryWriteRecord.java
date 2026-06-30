package com.ispf.server.history;

import com.ispf.server.persistence.entity.VariableSampleEntity;

import java.time.Instant;

/** Append-only historian row (write path; id assigned by DB). */
public record VariableHistoryWriteRecord(
        String objectPath,
        String variableName,
        String fieldName,
        Instant sampledAt,
        Instant observedAt,
        Double valueDouble,
        String valueText
) {

    public VariableHistoryWriteRecord {
        if (observedAt == null) {
            observedAt = sampledAt;
        }
    }

    public static VariableHistoryWriteRecord ingested(
            String objectPath,
            String variableName,
            String fieldName,
            Instant ingestedAt,
            Double valueDouble,
            String valueText
    ) {
        return new VariableHistoryWriteRecord(
                objectPath,
                variableName,
                fieldName,
                ingestedAt,
                ingestedAt,
                valueDouble,
                valueText
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
                entity.getValueText()
        );
    }
}
