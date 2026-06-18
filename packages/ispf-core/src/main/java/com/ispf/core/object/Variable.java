package com.ispf.core.object;

import com.ispf.core.model.DataRecord;
import com.ispf.core.model.DataSchema;

import java.time.Instant;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

/**
 * A typed property on a {@link PlatformObject}.
 */
public class Variable {

    private final String name;
    private final DataSchema schema;
    private final boolean readable;
    private final boolean writable;
    private final String bindingExpression;
    private final AtomicReference<DataRecord> value = new AtomicReference<>();
    private volatile Instant updatedAt;

    public Variable(
            String name,
            DataSchema schema,
            boolean readable,
            boolean writable,
            String bindingExpression,
            DataRecord initialValue
    ) {
        this.name = name;
        this.schema = schema;
        this.readable = readable;
        this.writable = writable;
        this.bindingExpression = bindingExpression;
        if (initialValue != null) {
            value.set(initialValue);
            updatedAt = Instant.now();
        }
    }

    public String name() {
        return name;
    }

    public DataSchema schema() {
        return schema;
    }

    public boolean readable() {
        return readable;
    }

    public boolean writable() {
        return writable;
    }

    public Optional<String> bindingExpression() {
        return Optional.ofNullable(bindingExpression);
    }

    public Optional<DataRecord> value() {
        return Optional.ofNullable(value.get());
    }

    public Optional<Instant> updatedAt() {
        return Optional.ofNullable(updatedAt);
    }

    public void setValue(DataRecord newValue) {
        if (!writable) {
            throw new IllegalStateException("Variable is read-only: " + name);
        }
        value.set(newValue);
        updatedAt = Instant.now();
    }

    /**
     * Updates a computed/bound variable without requiring write permission.
     */
    public void setComputedValue(DataRecord newValue) {
        value.set(newValue);
        updatedAt = Instant.now();
    }
}
