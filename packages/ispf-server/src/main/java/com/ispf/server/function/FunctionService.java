package com.ispf.server.function;

import com.ispf.core.object.FunctionDescriptor;
import com.ispf.core.object.ObjectType;
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
import com.ispf.server.plugin.blueprint.SystemObjectStructureService;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class FunctionService {

    private final ObjectManager objectManager;
    private final List<FunctionHandler> handlers;
    private final ApplicationFunctionStore applicationFunctionStore;
    private final FunctionInvokeAuditService auditService;
    private final ApplicationSchemaSession schemaSession;
    private final SystemObjectStructureService structureService;
    private final FunctionInvokeAccessService invokeAccessService;

    public FunctionService(
            ObjectManager objectManager,
            List<FunctionHandler> handlers,
            ApplicationFunctionStore applicationFunctionStore,
            FunctionInvokeAuditService auditService,
            ApplicationSchemaSession schemaSession,
            SystemObjectStructureService structureService,
            FunctionInvokeAccessService invokeAccessService
    ) {
        this.objectManager = objectManager;
        this.handlers = handlers;
        this.applicationFunctionStore = applicationFunctionStore;
        this.auditService = auditService;
        this.schemaSession = schemaSession;
        this.structureService = structureService;
        this.invokeAccessService = invokeAccessService;
    }

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
            DataRecord resolvedInput = resolveInput(input, descriptor);
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
        PlatformObject node = objectManager.tree().findByPath(objectPath).orElse(null);
        if (node == null || node.type() != ObjectType.DATA_SOURCE) {
            return;
        }
        if (DataSourceFunctionSupport.EXECUTE_QUERY_FUNCTION_NAME.equals(functionName)) {
            structureService.ensureDataSourceStructure(objectPath);
        }
    }

    private record ResolvedInvocation(
            FunctionHandler handler,
            FunctionDescriptor descriptor,
            DataRecord resolvedInput,
            String appId
    ) {
    }

    public DataRecord invoke(String objectPath, String functionName, DataRecord input) {
        return invoke(objectPath, functionName, DataRecordPayloadResolver.fromRecord(input));
    }

    private static DataRecord resolveInput(DataRecordPayloadRequest input, FunctionDescriptor descriptor) {
        DataSchema schema = descriptor != null
                ? descriptor.inputSchema()
                : DataSchema.builder("voidInput").build();
        return DataRecordPayloadResolver.resolve(schema, input);
    }

    private String resolveAppId(String objectPath, String functionName) {
        return applicationFunctionStore.findLatest(objectPath, functionName)
                .map(ApplicationFunctionHandler.DeployedFunction::appId)
                .orElse(null);
    }
}
