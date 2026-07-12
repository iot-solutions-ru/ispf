package com.ispf.server.api.dto;

import com.ispf.core.object.HistorySampleMode;
import com.ispf.core.object.Variable;
import com.ispf.core.object.VariableStorageMode;
import com.ispf.core.model.DataRecord;

import java.time.Instant;
import java.util.List;

public record VariableDto(
        String name,
        DataRecord value,
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
        return new VariableDto(
                variable.name(),
                variable.value().orElse(null),
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
}
