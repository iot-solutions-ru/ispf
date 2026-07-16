package com.ispf.server.function.java;

import com.ispf.core.model.DataRecord;
import com.ispf.core.object.FunctionDescriptor;
import com.ispf.core.object.PlatformObject;
import com.ispf.server.function.FunctionHandler;
import com.ispf.server.object.ObjectManager;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

@Component
@Order(-2)
public class JavaFunctionHandler implements FunctionHandler {

    private final ObjectManager objectManager;
    private final JavaFunctionRuntimeService runtimeService;

    public JavaFunctionHandler(ObjectManager objectManager, JavaFunctionRuntimeService runtimeService) {
        this.objectManager = objectManager;
        this.runtimeService = runtimeService;
    }

    @Override
    public boolean supports(String objectPath, String functionName) {
        if (!runtimeService.isEnabled()) {
            return false;
        }
        PlatformObject node = objectManager.tree().findByPath(objectPath).orElse(null);
        if (node == null) {
            return false;
        }
        FunctionDescriptor descriptor = node.functions().get(functionName);
        return descriptor != null && descriptor.hasJavaBody();
    }

    @Override
    public DataRecord invoke(String objectPath, String functionName, DataRecord input) {
        return runtimeService.invoke(objectPath, functionName, input);
    }
}
