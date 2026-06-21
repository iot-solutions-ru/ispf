package com.ispf.server.api.dto;

import com.ispf.core.model.DataSchema;

import java.util.List;
import java.util.Map;

/**
 * Optional DataRecord body for REST invoke/fire endpoints.
 * {@code schema} may be omitted — platform uses the descriptor schema.
 */
public record DataRecordPayloadRequest(
        DataSchema schema,
        List<Map<String, Object>> rows
) {
}
