package com.ispf.server.datasource;

import com.ispf.core.model.DataSchema;
import com.ispf.core.model.FieldType;
import com.ispf.core.object.FunctionDescriptor;

public final class DataSourceFunctionSupport {

    public static final String EXECUTE_QUERY_FUNCTION_NAME = "executeQuery";

    public static final DataSchema EXECUTE_QUERY_INPUT_SCHEMA = DataSchema.builder("executeQueryInput")
            .field("query", FieldType.STRING)
            .field("paramsJson", FieldType.STRING)
            .field("maxRows", FieldType.INTEGER)
            .build();

    public static final DataSchema EXECUTE_QUERY_OUTPUT_SCHEMA = DataSchema.builder("executeQueryOutput")
            .field("kind", FieldType.STRING)
            .field("rowCount", FieldType.INTEGER)
            .field("updateCount", FieldType.INTEGER)
            .field("rowsJson", FieldType.STRING)
            .build();

    public static final FunctionDescriptor EXECUTE_QUERY_FUNCTION = new FunctionDescriptor(
            EXECUTE_QUERY_FUNCTION_NAME,
            "Execute read or write SQL against this data source",
            EXECUTE_QUERY_INPUT_SCHEMA,
            EXECUTE_QUERY_OUTPUT_SCHEMA
    );

    private DataSourceFunctionSupport() {
    }
}
