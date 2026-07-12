package com.ispf.expression;

import com.ispf.core.model.DataRecord;
import com.ispf.core.object.PlatformObject;
import com.ispf.core.ref.PlatformRef;

import java.util.Optional;

/**
 * Runtime context for platform bindings that need cross-object reads or function invocation.
 */
public interface BindingEvaluationContext {

    BindingEvaluationContext NONE = (objectPath, functionName, input) -> Optional.empty();

    Optional<DataRecord> invokeFunction(String objectPath, String functionName, DataRecord input);

    default Optional<Object> readRemoteField(String objectPath, String variableName, String field) {
        return Optional.empty();
    }

    default Optional<Boolean> fireEvent(String objectPath, String eventName) {
        return Optional.empty();
    }

    /**
     * Resolves inline OQ spec JSON or {@code @/variable} ref to spec string.
     */
    default Optional<String> resolveObjectQuerySpec(String specArg, PlatformObject ruleObject) {
        return Optional.empty();
    }

    default Optional<Object> queryScalar(String specJson, String ruleObjectPath, String aggregate, String field) {
        return Optional.empty();
    }

    default Optional<java.util.List<java.util.Map<String, Object>>> queryRows(String specJson, String ruleObjectPath) {
        return Optional.empty();
    }

    default boolean writeRemoteField(PlatformRef ref, Object value, String ruleObjectPath) {
        return false;
    }
}
