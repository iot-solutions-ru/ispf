package com.ispf.server.function;

import com.ispf.core.model.DataRecord;
import com.ispf.core.object.FunctionDescriptor;
import com.ispf.core.object.PlatformObject;
import com.ispf.server.application.data.ApplicationSchemaSession;
import com.ispf.server.object.ObjectManager;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Component
@Order(-1)
public class ExpressionFunctionHandler implements FunctionHandler {

    private final ObjectManager objectManager;
    private final ApplicationSchemaSession schemaSession;
    private final FunctionExpressionEvaluator expressionEvaluator;

    public ExpressionFunctionHandler(
            ObjectManager objectManager,
            ApplicationSchemaSession schemaSession,
            FunctionExpressionEvaluator expressionEvaluator
    ) {
        this.objectManager = objectManager;
        this.schemaSession = schemaSession;
        this.expressionEvaluator = expressionEvaluator;
    }

    @Override
    public boolean supports(String objectPath, String functionName) {
        PlatformObject node = objectManager.tree().findByPath(objectPath).orElse(null);
        if (node == null) {
            return false;
        }
        FunctionDescriptor descriptor = node.functions().get(functionName);
        return descriptor != null && descriptor.hasExpressionBody();
    }

    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public DataRecord invoke(String objectPath, String functionName, DataRecord input) {
        FunctionDescriptor descriptor = schemaSession.callWithPlatformCatalog(() -> {
            PlatformObject node = objectManager.require(objectPath);
            FunctionDescriptor fn = node.functions().get(functionName);
            if (fn == null || !fn.hasExpressionBody()) {
                throw new IllegalStateException("Expression function not found: " + functionName);
            }
            return fn;
        });
        FunctionExpressionBody body = FunctionExpressionBody.parse(descriptor.sourceBody());
        DataRecord[] outputHolder = new DataRecord[1];
        schemaSession.runWithPlatformCatalog(() -> {
            PlatformObject node = objectManager.require(objectPath);
            outputHolder[0] = expressionEvaluator.evaluate(
                    node,
                    body,
                    input,
                    descriptor.outputSchema()
            );
        });
        return outputHolder[0];
    }
}
