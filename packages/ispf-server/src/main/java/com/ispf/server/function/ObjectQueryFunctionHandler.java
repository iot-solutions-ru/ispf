package com.ispf.server.function;

import com.ispf.core.model.DataRecord;
import com.ispf.core.model.DataSchema;
import com.ispf.core.model.FieldType;
import com.ispf.core.object.FunctionDescriptor;
import com.ispf.core.object.PlatformObject;
import com.ispf.expression.OqRowsJson;
import com.ispf.server.application.data.ApplicationSchemaSession;
import com.ispf.server.object.ObjectManager;
import com.ispf.server.query.ObjectQueryPatchService;
import com.ispf.server.query.ObjectQueryService;
import com.ispf.server.query.oq.ObjectQueryResult;
import com.ispf.server.query.oq.ObjectQuerySpec;
import com.ispf.server.query.oq.ObjectQuerySpecParser;
import org.springframework.context.annotation.Lazy;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;

import java.util.LinkedHashMap;
import java.util.Map;

@Component
@Order(-2)
public class ObjectQueryFunctionHandler implements FunctionHandler {

    private final ObjectManager objectManager;
    private final ApplicationSchemaSession schemaSession;
    private final ObjectQueryService objectQueryService;
    private final ObjectQuerySpecParser objectQuerySpecParser;
    private final ObjectQueryPatchService objectQueryPatchService;

    public ObjectQueryFunctionHandler(
            ObjectManager objectManager,
            ApplicationSchemaSession schemaSession,
            @Lazy ObjectQueryService objectQueryService,
            @Lazy ObjectQueryPatchService objectQueryPatchService,
            ObjectMapper objectMapper
    ) {
        this.objectManager = objectManager;
        this.schemaSession = schemaSession;
        this.objectQueryService = objectQueryService;
        this.objectQuerySpecParser = new ObjectQuerySpecParser(objectMapper);
        this.objectQueryPatchService = objectQueryPatchService;
    }

    @Override
    public boolean supports(String objectPath, String functionName) {
        PlatformObject node = objectManager.tree().findByPath(objectPath).orElse(null);
        if (node == null) {
            return false;
        }
        FunctionDescriptor descriptor = node.functions().get(functionName);
        return descriptor != null && descriptor.hasObjectQueryBody();
    }

    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public DataRecord invoke(String objectPath, String functionName, DataRecord input) {
        FunctionDescriptor descriptor = schemaSession.callWithPlatformCatalog(() -> {
            PlatformObject node = objectManager.require(objectPath);
            FunctionDescriptor fn = node.functions().get(functionName);
            if (fn == null || !fn.hasObjectQueryBody()) {
                throw new IllegalStateException("Object-query function not found: " + functionName);
            }
            return fn;
        });
        String patch = readPatch(input);
        if (patch != null && !patch.isBlank()) {
            ObjectQueryPatchService.PatchResult patchResult = schemaSession.callWithPlatformCatalog(() ->
                    objectQueryPatchService.apply(patch, objectPath)
            );
            return toOutput(descriptor.outputSchema(), Map.of(
                    "rows", "[]",
                    "rowCount", 0,
                    "patchApplied", patchResult.failed() == 0,
                    "patchesApplied", patchResult.applied()
            ));
        }
        ObjectQuerySpec spec = objectQuerySpecParser.parse(descriptor.sourceBody());
        ObjectQueryResult result = schemaSession.callWithPlatformCatalog(() ->
                objectQueryService.execute(spec, objectPath)
        );
        return toOutput(descriptor.outputSchema(), Map.of(
                "rows", OqRowsJson.encode(result.rows()),
                "rowCount", result.rowCount(),
                "patchApplied", false,
                "patchesApplied", 0
        ));
    }

    private static String readPatch(DataRecord input) {
        if (input == null || input.rowCount() == 0) {
            return null;
        }
        Map<String, Object> row = input.firstRow();
        Object patch = row.get("patch");
        if (patch != null && !String.valueOf(patch).isBlank()) {
            return String.valueOf(patch);
        }
        Object patches = row.get("patches");
        if (patches != null) {
            return String.valueOf(patches);
        }
        return null;
    }

    private static DataRecord toOutput(DataSchema schema, Map<String, Object> values) {
        DataSchema output = schema != null ? schema : DataSchema.builder("objectQueryOutput")
                .field("rows", FieldType.STRING)
                .field("rowCount", FieldType.INTEGER)
                .field("patchApplied", FieldType.BOOLEAN)
                .field("patchesApplied", FieldType.INTEGER)
                .build();
        Map<String, Object> row = new LinkedHashMap<>();
        for (var field : output.fields()) {
            row.put(field.name(), values.getOrDefault(field.name(), null));
        }
        return DataRecord.single(output, row);
    }
}
