package com.ispf.server.function;

import com.ispf.core.object.PlatformObject;
import com.ispf.core.object.FunctionDescriptor;
import com.ispf.core.model.DataRecord;
import com.ispf.core.model.DataSchema;
import com.ispf.server.application.function.ApplicationFunctionHandler;
import com.ispf.server.application.function.ApplicationFunctionStore;
import com.ispf.server.application.function.FunctionInvokeAuditService;
import com.ispf.server.object.ObjectManager;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class FunctionService {

    private final ObjectManager objectManager;
    private final List<FunctionHandler> handlers;
    private final ApplicationFunctionStore applicationFunctionStore;
    private final FunctionInvokeAuditService auditService;

    public FunctionService(
            ObjectManager objectManager,
            List<FunctionHandler> handlers,
            ApplicationFunctionStore applicationFunctionStore,
            FunctionInvokeAuditService auditService
    ) {
        this.objectManager = objectManager;
        this.handlers = handlers;
        this.applicationFunctionStore = applicationFunctionStore;
        this.auditService = auditService;
    }

    public DataRecord invoke(String objectPath, String functionName, DataRecord input) {
        PlatformObject node = objectManager.require(objectPath);
        FunctionDescriptor descriptor = node.functions().get(functionName);
        String appId = resolveAppId(objectPath, functionName);

        for (FunctionHandler handler : handlers) {
            if (handler.supports(objectPath, functionName)) {
                DataRecord resolvedInput = resolveInput(input, descriptor);
                try {
                    DataRecord result = handler.invoke(objectPath, functionName, resolvedInput);
                    auditService.record(appId, objectPath, functionName, true, null);
                    return result;
                } catch (RuntimeException ex) {
                    auditService.record(appId, objectPath, functionName, false, ex.getMessage());
                    throw ex;
                }
            }
        }

        if (descriptor == null) {
            throw new IllegalArgumentException("Unknown function: " + functionName);
        }

        DataRecord resolvedInput = input != null ? input : DataRecord.empty(descriptor.inputSchema());
        IllegalStateException ex = new IllegalStateException("No handler registered for function: " + functionName);
        auditService.record(appId, objectPath, functionName, false, ex.getMessage());
        throw ex;
    }

    private static DataRecord resolveInput(DataRecord input, FunctionDescriptor descriptor) {
        if (input != null) {
            return input;
        }
        if (descriptor != null) {
            return DataRecord.empty(descriptor.inputSchema());
        }
        return DataRecord.empty(DataSchema.builder("voidInput").build());
    }

    private String resolveAppId(String objectPath, String functionName) {
        return applicationFunctionStore.findLatest(objectPath, functionName)
                .map(ApplicationFunctionHandler.DeployedFunction::appId)
                .orElse(null);
    }
}
