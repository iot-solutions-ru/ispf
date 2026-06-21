package com.ispf.server.object;

import com.ispf.core.model.DataRecord;
import com.ispf.core.object.PlatformObject;
import com.ispf.core.object.Variable;
import com.ispf.expression.BindingEvaluationContext;
import com.ispf.server.function.FunctionService;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;

import java.util.Optional;

/**
 * Server-side {@link BindingEvaluationContext} for cross-object reads and function invocation.
 */
@Component
public class ServerBindingEvaluationContext implements BindingEvaluationContext {

    private static final ThreadLocal<Boolean> INVOKE_GUARD = ThreadLocal.withInitial(() -> false);

    private final ObjectManager objectManager;
    private final ObjectProvider<FunctionService> functionService;

    public ServerBindingEvaluationContext(
            @Lazy ObjectManager objectManager,
            ObjectProvider<FunctionService> functionService
    ) {
        this.objectManager = objectManager;
        this.functionService = functionService;
    }

    @Override
    public Optional<DataRecord> invokeFunction(String objectPath, String functionName, DataRecord input) {
        if (INVOKE_GUARD.get()) {
            return Optional.empty();
        }
        INVOKE_GUARD.set(true);
        try {
            return Optional.of(functionService.getObject().invoke(objectPath, functionName, input));
        } catch (RuntimeException ignored) {
            return Optional.empty();
        } finally {
            INVOKE_GUARD.set(false);
        }
    }

    @Override
    public Optional<Object> readRemoteField(String objectPath, String variableName, String field) {
        return objectManager.tree().findByPath(objectPath)
                .flatMap(node -> node.getVariable(variableName))
                .flatMap(Variable::value)
                .filter(record -> record.rowCount() > 0)
                .map(record -> record.firstRow().get(field))
                .filter(value -> value != null);
    }
}
