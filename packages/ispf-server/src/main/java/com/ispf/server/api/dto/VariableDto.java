package com.ispf.server.api.dto;

import com.ispf.core.object.Variable;
import com.ispf.core.model.DataRecord;

import java.time.Instant;

public record VariableDto(
        String name,
        DataRecord value,
        boolean readable,
        boolean writable,
        String bindingExpression,
        Instant updatedAt,
        boolean historyEnabled,
        Integer historyRetentionDays
) {
    public static VariableDto from(Variable variable) {
        return new VariableDto(
                variable.name(),
                variable.value().orElse(null),
                variable.readable(),
                variable.writable(),
                variable.bindingExpression().orElse(null),
                variable.updatedAt().orElse(null),
                variable.historyEnabled(),
                variable.historyRetentionDays().orElse(null)
        );
    }
}
