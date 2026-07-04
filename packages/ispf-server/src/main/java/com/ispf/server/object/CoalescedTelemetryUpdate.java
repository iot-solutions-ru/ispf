package com.ispf.server.object;

import com.ispf.core.model.DataRecord;

import java.time.Instant;

/** One telemetry sample after ingress/coalesce (L3 → L4). */
public record CoalescedTelemetryUpdate(
        String path,
        String variableName,
        DataRecord value,
        Instant observedAt
) {
}
