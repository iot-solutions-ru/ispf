package com.ispf.server.api.dto;

import com.ispf.core.object.HistorySampleMode;
import com.ispf.core.object.Variable;
import com.ispf.core.object.VariableStorageMode;
import com.ispf.core.model.DataRecord;
import com.ispf.core.model.DataSchema;

import java.time.Instant;
import java.util.List;

public record VariableDto(
        String name,
        DataRecord value,
        /** Variable definition schema (stable even when live value is unset). */
        DataSchema schema,
        boolean readable,
        boolean writable,
        Instant updatedAt,
        boolean historyEnabled,
        Integer historyRetentionDays,
        String historySampleMode,
        boolean includePreviousValueInEvent,
        String storageMode,
        String telemetryPublishMode,
        List<String> readRoles,
        List<String> writeRoles
) {
    public static VariableDto from(Variable variable) {
        DataRecord value = variable.value().orElse(null);
        DataSchema definition = variable.schema();
        if (definition != null && !definition.fields().isEmpty()) {
            if (value == null) {
                value = DataRecord.empty(definition);
            } else if (shouldUseDefinitionSchema(definition, value.schema())) {
                value = new DataRecord(definition, value.rows());
            }
        }
        return new VariableDto(
                variable.name(),
                value,
                definition,
                variable.readable(),
                variable.writable(),
                variable.updatedAt().orElse(null),
                variable.historyEnabled(),
                variable.historyRetentionDays().orElse(null),
                variable.historySampleMode().name(),
                variable.includePreviousValueInEvent(),
                variable.storageMode().name(),
                variable.telemetryPublishModeOverride().orElse(null),
                variable.readRoles(),
                variable.writeRoles()
        );
    }

    public HistorySampleMode parsedHistorySampleMode() {
        return HistorySampleMode.parse(historySampleMode);
    }

    public VariableStorageMode parsedStorageMode() {
        return VariableStorageMode.parse(storageMode);
    }

    private static boolean shouldUseDefinitionSchema(DataSchema definition, DataSchema payload) {
        if (payload == null || payload.fields().isEmpty()) {
            return true;
        }
        if (definition.fieldCount() > payload.fieldCount()) {
            return true;
        }
        for (var field : definition.fields()) {
            if (payload.field(field.name()).isEmpty()) {
                return true;
            }
        }
        return false;
    }
}
