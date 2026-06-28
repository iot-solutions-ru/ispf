package com.ispf.core.function;

import com.ispf.core.model.DataRecord;

/**
 * Contract for user-authored Java functions stored on object nodes.
 * <p>
 * Implement this interface in {@code sourceBody} when {@code sourceType} is {@code java}.
 * The platform compiles the source on save and invokes {@link #invoke} at runtime.
 */
public interface ObjectJavaFunction {

    DataRecord invoke(DataRecord input, JavaFunctionContext context);
}
