package com.ispf.server.object;

import com.ispf.core.model.DataRecord;
import com.ispf.core.ref.PlatformRef;
import com.ispf.expression.BindingEvaluationContext;
import com.ispf.server.event.EventService;
import com.ispf.server.function.FunctionService;
import com.ispf.server.ref.PlatformRefExecutor;
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
    private final EventService eventService;
    private final PlatformRefExecutor platformRefExecutor;

    public ServerBindingEvaluationContext(
            @Lazy ObjectManager objectManager,
            ObjectProvider<FunctionService> functionService,
            EventService eventService,
            @Lazy PlatformRefExecutor platformRefExecutor
    ) {
        this.objectManager = objectManager;
        this.functionService = functionService;
        this.eventService = eventService;
        this.platformRefExecutor = platformRefExecutor;
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
        return platformRefExecutor.read(
                PlatformRef.variable(objectPath, variableName, field),
                objectPath
        );
    }

    @Override
    public Optional<Boolean> fireEvent(String objectPath, String eventName) {
        return platformRefExecutor.fire(
                PlatformRef.event(objectPath, eventName),
                objectPath,
                null
        ).map(ignored -> Boolean.TRUE);
    }
}
