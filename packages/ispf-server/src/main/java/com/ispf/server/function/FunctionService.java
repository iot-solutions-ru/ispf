package com.ispf.server.function;

import com.ispf.core.object.FunctionDescriptor;
import com.ispf.core.object.PlatformObject;
import com.ispf.core.model.DataRecord;
import com.ispf.core.model.DataSchema;
import com.ispf.server.api.dto.DataRecordPayloadRequest;
import com.ispf.server.api.dto.DataRecordPayloadResolver;
import com.ispf.server.application.data.ApplicationSchemaSession;
import com.ispf.server.application.function.ApplicationFunctionHandler;
import com.ispf.server.application.function.ApplicationFunctionStore;
import com.ispf.server.application.function.FunctionInvokeAuditService;
import com.ispf.server.datasource.DataSourceFunctionSupport;
import com.ispf.server.object.ObjectManager;
import com.ispf.server.spi.WorkflowFunctionCalls;
import com.ispf.server.plugin.blueprint.SystemObjectStructureService;
import org.springframework.stereotype.Service;
import tools.jackson.databind.ObjectMapper;

import java.util.List;

@Service
public class FunctionService implements WorkflowFunctionCalls {

    private final ObjectManager objectManager;
    private final List<FunctionHandler> handlers;
    private final ApplicationFunctionStore applicationFunctionStore;
    private final FunctionInvokeAuditService auditService;
    private final ApplicationSchemaSession schemaSession;
    private final SystemObjectStructureService structureService;
    private final FunctionInvokeAccessService invokeAccessService;
    private final ObjectMapper objectMapper;

    public FunctionService(
            ObjectManager objectManager,
            List<FunctionHandler> handlers,
            ApplicationFunctionStore applicationFunctionStore,
            FunctionInvokeAuditService auditService,
            ApplicationSchemaSession schemaSession,
            SystemObjectStructureService structureService,
            FunctionInvokeAccessService invokeAccessService,
            ObjectMapper objectMapper
    ) {
        this.objectManager = objectManager;
        this.handlers = handlers;
        this.applicationFunctionStore = applicationFunctionStore;
        this.auditService = auditService;
        this.schemaSession = schemaSession;
        this.structureService = structureService;
        this.invokeAccessService = invokeAccessService;
        this.objectMapper = objectMapper;
    }

    @Override
    public DataRecord invoke(String objectPath, String functionName) {
        return invoke(objectPath, functionName, (DataRecordPayloadRequest) null);
    }

    public DataRecord invoke(String objectPath, String functionName, DataRecordPayloadRequest input) {
        invokeAccessService.guardScriptOnlyFunction(objectPath, functionName, null);
        ensureBuiltinFunctionStructure(objectPath, functionName);
        ResolvedInvocation resolved = schemaSession.callWithPlatformCatalog(() -> {
            FunctionDescriptor descriptor = objectManager.tree().findByPath(objectPath)
                    .map(node -> node.functions().get(functionName))
                    .orElse(null);
            DataRecord resolvedInput = resolveInput(objectPath, functionName, input, descriptor);
            FunctionHandler handler = null;
            for (FunctionHandler candidate : handlers) {
                if (candidate.supports(objectPath, functionName)) {
                    handler = candidate;
                    break;
                }
            }
            String appId = resolveAppId(objectPath, functionName);
            return new ResolvedInvocation(handler, descriptor, resolvedInput, appId);
        });

        if (resolved.handler() != null) {
            try {
                DataRecord result = resolved.handler().invoke(objectPath, functionName, resolved.resolvedInput());
                auditService.record(
                        resolved.appId(),
                        objectPath,
                        functionName,
                        resolved.resolvedInput(),
                        result,
                        true,
                        null
                );
                return result;
            } catch (RuntimeException ex) {
                auditService.record(
                        resolved.appId(),
                        objectPath,
                        functionName,
                        resolved.resolvedInput(),
                        null,
                        false,
                        ex.getMessage()
                );
                throw ex;
            }
        }

        if (resolved.descriptor() == null) {
            throw new IllegalArgumentException("Unknown function: " + functionName);
        }

        IllegalStateException ex = new IllegalStateException("No handler registered for function: " + functionName);
        auditService.record(
                resolved.appId(),
                objectPath,
                functionName,
                resolved.resolvedInput(),
                null,
                false,
                ex.getMessage()
        );
        throw ex;
    }

    private void ensureBuiltinFunctionStructure(String objectPath, String functionName) {
        if (!DataSourceFunctionSupport.EXECUTE_QUERY_FUNCTION_NAME.equals(functionName)) {
            return;
        }
        PlatformObject node = objectManager.tree().findByPath(objectPath).orElse(null);
        if (node == null) {
            return;
        }
        FunctionDescriptor descriptor = node.functions().get(functionName);
        ExecuteQueryPathResolver.tryResolve(objectManager, objectPath, descriptor)
                .ifPresent(structureService::ensureDataSourceStructure);
    }

    private record ResolvedInvocation(
            FunctionHandler handler,
            FunctionDescriptor descriptor,
            DataRecord resolvedInput,
            String appId
    ) {
    }

    @Override
    public DataRecord invoke(String objectPath, String functionName, DataRecord input) {
        return invoke(objectPath, functionName, DataRecordPayloadResolver.fromRecord(input));
    }

    /**
     * Prefer the latest deployed application-function input schema when present so a redeploy
     * that relaxes required fields takes effect without waiting for tree {@code functions_json}
     * to be rewritten (ensureDescriptor historically only inserted missing descriptors).
     */
    private DataRecord resolveInput(
            String objectPath,
            String functionName,
            DataRecordPayloadRequest input,
            FunctionDescriptor descriptor
    ) {
        DataSchema schema = applicationFunctionStore.findLatest(objectPath, functionName)
                .map(ApplicationFunctionHandler.DeployedFunction::inputSchemaJson)
                .map(this::readInputSchemaJson)
                .orElseGet(() -> descriptor != null
                        ? descriptor.inputSchema()
                        : DataSchema.builder("voidInput").build());
        return FunctionInputSchema.apply(schema, input);
    }

    private DataSchema readInputSchemaJson(String json) {
        try {
            return objectMapper.readValue(json, DataSchema.class);
        } catch (Exception ex) {
            throw new IllegalStateException("Invalid deployed input schema JSON", ex);
        }
    }

    private String resolveAppId(String objectPath, String functionName) {
        return applicationFunctionStore.findLatest(objectPath, functionName)
                .map(ApplicationFunctionHandler.DeployedFunction::appId)
                .orElse(null);
    }
}
