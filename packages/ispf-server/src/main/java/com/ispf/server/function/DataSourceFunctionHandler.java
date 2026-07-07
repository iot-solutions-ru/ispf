package com.ispf.server.function;

import com.ispf.core.model.DataRecord;
import com.ispf.core.object.ObjectType;
import com.ispf.core.object.PlatformObject;
import com.ispf.server.datasource.DataSourceFunctionSupport;
import com.ispf.server.datasource.DataSourceObjectService;
import com.ispf.server.datasource.DataSourceQueryExecutor;
import com.ispf.server.datasource.DataSourceQueryResult;
import com.ispf.server.object.ObjectManager;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Component
@Order(5)
public class DataSourceFunctionHandler implements FunctionHandler {

    private static final TypeReference<List<Object>> PARAMS_TYPE = new TypeReference<>() {
    };

    private final ObjectManager objectManager;
    private final DataSourceObjectService dataSourceObjectService;
    private final DataSourceQueryExecutor queryExecutor;
    private final ObjectMapper objectMapper;

    public DataSourceFunctionHandler(
            ObjectManager objectManager,
            DataSourceObjectService dataSourceObjectService,
            DataSourceQueryExecutor queryExecutor,
            ObjectMapper objectMapper
    ) {
        this.objectManager = objectManager;
        this.dataSourceObjectService = dataSourceObjectService;
        this.queryExecutor = queryExecutor;
        this.objectMapper = objectMapper;
    }

    @Override
    public boolean supports(String objectPath, String functionName) {
        if (!DataSourceFunctionSupport.EXECUTE_QUERY_FUNCTION_NAME.equals(functionName)) {
            return false;
        }
        PlatformObject node = objectManager.tree().findByPath(objectPath).orElse(null);
        return node != null && node.type() == ObjectType.DATA_SOURCE;
    }

    @Override
    public DataRecord invoke(String objectPath, String functionName, DataRecord input) {
        dataSourceObjectService.ensureStructure(objectPath);
        Map<String, Object> row = input != null && input.rowCount() > 0
                ? input.firstRow()
                : Map.of();
        String query = stringValue(row.get("query"));
        if (query.isBlank()) {
            throw new IllegalArgumentException("query is required");
        }
        List<Object> params = parseParams(row.get("paramsJson"));
        Integer maxRows = intValue(row.get("maxRows"));

        DataSourceQueryResult result = queryExecutor.execute(objectPath, query, params, maxRows);
        return toOutput(result);
    }

    private DataRecord toOutput(DataSourceQueryResult result) {
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("kind", result.kind());
        out.put("rowCount", result.rowCount());
        out.put("updateCount", result.updateCount());
        try {
            out.put("rowsJson", objectMapper.writeValueAsString(result.rows()));
        } catch (Exception ex) {
            throw new IllegalStateException("Failed to serialize rows", ex);
        }
        return DataRecord.single(DataSourceFunctionSupport.EXECUTE_QUERY_OUTPUT_SCHEMA, out);
    }

    private List<Object> parseParams(Object raw) {
        if (raw == null) {
            return List.of();
        }
        String text = String.valueOf(raw).trim();
        if (text.isBlank()) {
            return List.of();
        }
        try {
            return new ArrayList<>(objectMapper.readValue(text, PARAMS_TYPE));
        } catch (Exception ex) {
            throw new IllegalArgumentException("paramsJson must be a JSON array: " + ex.getMessage());
        }
    }

    private static String stringValue(Object value) {
        return value != null ? String.valueOf(value) : "";
    }

    private static Integer intValue(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof Number number) {
            return number.intValue();
        }
        String text = String.valueOf(value).trim();
        if (text.isBlank()) {
            return null;
        }
        return Integer.parseInt(text);
    }
}
