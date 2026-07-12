package com.ispf.server.query;

import com.ispf.core.model.DataSchema;
import com.ispf.core.model.FieldType;
import com.ispf.core.object.FunctionDescriptor;

/**
 * Built-in object-query function metadata for {@code root.platform.queries} catalog.
 */
public final class ObjectQueryFunctionSupport {

    public static final String RUN_FUNCTION_NAME = "run";

    public static final DataSchema RUN_INPUT_SCHEMA = DataSchema.builder("objectQueryInput")
            .field("patch", FieldType.STRING)
            .build();

    public static final DataSchema RUN_OUTPUT_SCHEMA = DataSchema.builder("objectQueryOutput")
            .field("rows", FieldType.STRING)
            .field("rowCount", FieldType.INTEGER)
            .field("patchApplied", FieldType.BOOLEAN)
            .field("patchesApplied", FieldType.INTEGER)
            .build();

    private ObjectQueryFunctionSupport() {
    }

    public static FunctionDescriptor runFunction(String sourceBody) {
        return new FunctionDescriptor(
                RUN_FUNCTION_NAME,
                "Execute object query (OQ) over platform tree",
                RUN_INPUT_SCHEMA,
                RUN_OUTPUT_SCHEMA,
                "object-query",
                sourceBody,
                null,
                null
        );
    }
}
