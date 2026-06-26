package com.ispf.server.history;

import com.ispf.server.persistence.entity.VariableSampleEntity;

import java.time.Instant;

/** Append-only historian row (write path; id assigned by DB). */
public record VariableHistoryWriteRecord(
        String objectPath,
        String variableName,
        String fieldName,
        Instant sampledAt,
        Double valueDouble,
        String valueText
) {

    public static VariableHistoryWriteRecord fromEntity(VariableSampleEntity entity) {
        return new VariableHistoryWriteRecord(
                entity.getObjectPath(),
                entity.getVariableName(),
                entity.getFieldName(),
                entity.getSampledAt(),
                entity.getValueDouble(),
                entity.getValueText()
        );
    }
}
