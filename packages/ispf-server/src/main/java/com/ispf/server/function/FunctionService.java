package com.ispf.server.function;

import com.ispf.core.object.PlatformObject;
import com.ispf.core.object.FunctionDescriptor;
import com.ispf.core.model.DataRecord;
import com.ispf.server.object.ObjectManager;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class FunctionService {

    private final ObjectManager objectManager;
    private final List<FunctionHandler> handlers;

    public FunctionService(ObjectManager objectManager, List<FunctionHandler> handlers) {
        this.objectManager = objectManager;
        this.handlers = handlers;
    }

    public DataRecord invoke(String objectPath, String functionName, DataRecord input) {
        PlatformObject node = objectManager.require(objectPath);
        FunctionDescriptor descriptor = node.functions().get(functionName);
        if (descriptor == null) {
            throw new IllegalArgumentException("Unknown function: " + functionName);
        }

        DataRecord resolvedInput = input != null ? input : DataRecord.empty(descriptor.inputSchema());

        for (FunctionHandler handler : handlers) {
            if (handler.supports(objectPath, functionName)) {
                return handler.invoke(objectPath, functionName, resolvedInput);
            }
        }

        throw new IllegalStateException("No handler registered for function: " + functionName);
    }
}
