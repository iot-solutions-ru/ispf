package com.ispf.expression;

import com.ispf.core.model.DataRecord;

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
}
