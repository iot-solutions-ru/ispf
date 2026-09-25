package com.ispf.server.object;

import com.ispf.core.model.DataRecord;
import com.ispf.core.object.PlatformObject;
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
        String target = objectPath + "." + functionName;
        if (INVOKE_GUARD.get()) {
            throw new IllegalStateException("Nested function call is not allowed: " + target);
        }
        INVOKE_GUARD.set(true);
        try {
            return Optional.of(functionService.getObject().invoke(objectPath, functionName, input));
        } catch (RuntimeException ex) {
            throw failed("Function call failed", target, ex);
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
        platformRefExecutor.fire(
                PlatformRef.event(objectPath, eventName),
                objectPath,
                null
        );
        return Optional.of(Boolean.TRUE);
    }

    @Override
    public Optional<String> resolveObjectQuerySpec(String specArg, PlatformObject ruleObject) {
        if (specArg == null || specArg.isBlank()) {
            return Optional.empty();
        }
        String trimmed = specArg.trim();
        // Strip expression string quotes before deciding ref vs JSON. A quoted Object Query
        // spec often contains "/" inside path values; treating those as variable sources
        // made queryScalar('{"…path…"}', …) fail as an invalid ref.
        if (trimmed.length() >= 2 && trimmed.startsWith("'") && trimmed.endsWith("'")) {
            trimmed = trimmed.substring(1, trimmed.length() - 1).trim();
        }
        if (trimmed.isEmpty()) {
            return Optional.empty();
        }
        if (trimmed.startsWith("@/") || (trimmed.contains("/") && !trimmed.startsWith("{"))) {
            try {
                PlatformRef ref = PlatformRefParser.parseVariableSource(trimmed);
                if (ref.isCurrentObject()) {
                    return platformRefExecutor.readLocal(ruleObject, ref).map(String::valueOf);
                }
                return readRemoteField(ref.object(), ref.name(), ref.field()).map(String::valueOf);
            } catch (RuntimeException ex) {
                throw failed("Object query spec failed", trimmed, ex);
            }
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
            throw failed("queryScalar failed", ruleObjectPath, ex);
        }
    }

    @Override
    public Optional<List<Map<String, Object>>> queryRows(String specJson, String ruleObjectPath) {
        try {
            ObjectQuerySpec spec = objectQuerySpecParser.parse(specJson);
            ObjectQueryResult result = objectQueryService.execute(spec, ruleObjectPath);
            return Optional.of(result.rows());
        } catch (RuntimeException ex) {
            throw failed("queryRows failed", ruleObjectPath, ex);
        }
    }

    @Override
    public boolean writeRemoteField(PlatformRef ref, Object value, String ruleObjectPath) {
        return platformRefExecutor.write(ref, value, ruleObjectPath);
    }

    private static IllegalStateException failed(String action, String target, RuntimeException cause) {
        String detail = cause.getMessage() != null ? cause.getMessage() : cause.getClass().getSimpleName();
        return new IllegalStateException(action + ": " + target + ": " + detail, cause);
    }
}
