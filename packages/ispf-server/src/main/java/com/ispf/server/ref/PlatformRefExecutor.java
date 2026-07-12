package com.ispf.server.ref;

import com.ispf.core.model.DataRecord;
import com.ispf.core.object.PlatformObject;
import com.ispf.core.object.Variable;
import com.ispf.core.ref.PlatformRef;
import com.ispf.core.ref.PlatformRefKind;
import com.ispf.expression.ExpressionException;
import com.ispf.server.event.EventService;
import com.ispf.server.function.FunctionService;
import com.ispf.server.object.ObjectManager;
import org.springframework.stereotype.Component;

import java.util.Optional;

/**
 * Executes read / call / fire for {@link PlatformRef} (ADR-0043).
 */
@Component
public class PlatformRefExecutor {

    private final ObjectManager objectManager;
    private final FunctionService functionService;
    private final EventService eventService;

    public PlatformRefExecutor(
            ObjectManager objectManager,
            FunctionService functionService,
            EventService eventService
    ) {
        this.objectManager = objectManager;
        this.functionService = functionService;
        this.eventService = eventService;
    }

    public Optional<Object> read(PlatformRef ref, String ruleObjectPath) {
        PlatformRef resolved = PlatformRefResolver.resolve(ref, ruleObjectPath);
        if (resolved.kind() != PlatformRefKind.VARIABLE) {
            throw new ExpressionException("read() requires variable ref: " + ref);
        }
        return objectManager.tree().findByPath(resolved.object())
                .flatMap(node -> node.getVariable(resolved.name()))
                .flatMap(Variable::value)
                .filter(record -> record.rowCount() > 0)
                .map(record -> record.firstRow().get(resolved.field()))
                .filter(value -> value != null);
    }

    public Optional<Object> readLocal(PlatformObject object, PlatformRef ref) {
        PlatformRef resolved = ref.isCurrentObject()
                ? ref.resolveObject(object.path())
                : ref;
        if (resolved.kind() != PlatformRefKind.VARIABLE) {
            throw new ExpressionException("read() requires variable ref: " + ref);
        }
        if (!resolved.object().equals(object.path())) {
            return read(ref, object.path());
        }
        return object.getVariable(resolved.name())
                .flatMap(Variable::value)
                .filter(record -> record.rowCount() > 0)
                .map(record -> record.firstRow().get(resolved.field()))
                .filter(value -> value != null);
    }

    public Optional<DataRecord> call(PlatformRef ref, String ruleObjectPath, DataRecord input) {
        PlatformRef resolved = PlatformRefResolver.resolve(ref, ruleObjectPath);
        if (resolved.kind() != PlatformRefKind.FUNCTION) {
            throw new ExpressionException("call() requires function ref: " + ref);
        }
        try {
            return Optional.of(functionService.invoke(resolved.object(), resolved.name(), input));
        } catch (RuntimeException ex) {
            return Optional.empty();
        }
    }

    public Optional<DataRecord> fire(PlatformRef ref, String ruleObjectPath, DataRecord payload) {
        PlatformRef resolved = PlatformRefResolver.resolve(ref, ruleObjectPath);
        if (resolved.kind() != PlatformRefKind.EVENT) {
            throw new ExpressionException("fire() requires event ref: " + ref);
        }
        try {
            eventService.fire(resolved.object(), resolved.name(), payload);
            return Optional.ofNullable(payload);
        } catch (RuntimeException ex) {
            return Optional.empty();
        }
    }

    public boolean write(PlatformRef ref, Object value, String ruleObjectPath) {
        PlatformRef resolved = PlatformRefResolver.resolve(ref, ruleObjectPath);
        if (resolved.kind() != PlatformRefKind.VARIABLE) {
            throw new ExpressionException("write() requires variable ref: " + ref);
        }
        return objectManager.tree().findByPath(resolved.object())
                .flatMap(node -> node.getVariable(resolved.name()))
                .map(variable -> {
                    var schema = variable.schema();
                    var row = new java.util.LinkedHashMap<String, Object>();
                    if (schema != null && schema.fields() != null && !schema.fields().isEmpty()) {
                        String field = resolved.field() != null ? resolved.field() : schema.fields().get(0).name();
                        row.put(field, value);
                    } else {
                        row.put(resolved.field() != null ? resolved.field() : "value", value);
                    }
                    objectManager.setVariableValue(resolved.object(), resolved.name(), DataRecord.single(schema, row));
                    return true;
                })
                .orElse(false);
    }
}
