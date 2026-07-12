package com.ispf.server.object;

import com.ispf.core.model.DataRecord;
import com.ispf.core.object.PlatformObject;
import com.ispf.core.object.Variable;
import com.ispf.core.ref.PlatformRef;
import com.ispf.core.ref.PlatformRefParser;
import com.ispf.expression.BindingEvaluationContext;
import com.ispf.server.event.EventService;
import com.ispf.server.function.FunctionService;
import com.ispf.server.query.ObjectQueryService;
import com.ispf.server.query.oq.ObjectQueryResult;
import com.ispf.server.query.oq.ObjectQuerySpec;
import com.ispf.server.query.oq.ObjectQuerySpecParser;
import com.ispf.server.ref.PlatformRefExecutor;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

import java.util.List;
import java.util.Map;
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
    private final ObjectQueryService objectQueryService;
    private final ObjectQuerySpecParser objectQuerySpecParser;

    public ServerBindingEvaluationContext(
            @Lazy ObjectManager objectManager,
            ObjectProvider<FunctionService> functionService,
            EventService eventService,
            @Lazy PlatformRefExecutor platformRefExecutor,
            @Lazy ObjectQueryService objectQueryService,
            ObjectMapper objectMapper
    ) {
        this.objectManager = objectManager;
        this.functionService = functionService;
        this.eventService = eventService;
        this.platformRefExecutor = platformRefExecutor;
        this.objectQueryService = objectQueryService;
        this.objectQuerySpecParser = new ObjectQuerySpecParser(objectMapper);
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

    @Override
    public Optional<String> resolveObjectQuerySpec(String specArg, PlatformObject ruleObject) {
        if (specArg == null || specArg.isBlank()) {
            return Optional.empty();
        }
        String trimmed = specArg.trim();
        if (trimmed.startsWith("@/") || (trimmed.contains("/") && !trimmed.startsWith("{"))) {
            try {
                PlatformRef ref = PlatformRefParser.parseVariableSource(trimmed);
                if (ref.isCurrentObject()) {
                    return ruleObject.getVariable(ref.name())
                            .flatMap(Variable::value)
                            .map(record -> String.valueOf(record.firstRow().get(ref.field())));
                }
                return readRemoteField(ref.object(), ref.name(), ref.field()).map(String::valueOf);
            } catch (RuntimeException ex) {
                return Optional.empty();
            }
        }
        if (trimmed.length() >= 2 && trimmed.startsWith("'") && trimmed.endsWith("'")) {
            return Optional.of(trimmed.substring(1, trimmed.length() - 1));
        }
        if (trimmed.startsWith("{")) {
            return Optional.of(trimmed);
        }
        return Optional.of(trimmed);
    }

    @Override
    public Optional<Object> queryScalar(String specJson, String ruleObjectPath, String aggregate, String field) {
        try {
            ObjectQuerySpec spec = objectQuerySpecParser.parse(specJson);
            return Optional.of(objectQueryService.executeAggregate(spec, aggregate, field, ruleObjectPath));
        } catch (RuntimeException ex) {
            return Optional.empty();
        }
    }

    @Override
    public Optional<List<Map<String, Object>>> queryRows(String specJson, String ruleObjectPath) {
        try {
            ObjectQuerySpec spec = objectQuerySpecParser.parse(specJson);
            ObjectQueryResult result = objectQueryService.execute(spec, ruleObjectPath);
            return Optional.of(result.rows());
        } catch (RuntimeException ex) {
            return Optional.empty();
        }
    }

    @Override
    public boolean writeRemoteField(PlatformRef ref, Object value, String ruleObjectPath) {
        return platformRefExecutor.write(ref, value, ruleObjectPath);
    }
}
