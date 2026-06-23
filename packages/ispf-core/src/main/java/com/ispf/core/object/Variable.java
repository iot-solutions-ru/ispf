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
    private final boolean historyEnabled;
    private final Integer historyRetentionDays;
    private final AtomicReference<DataRecord> value = new AtomicReference<>();
    private volatile Instant updatedAt;

    public Variable(
            String name,
            DataSchema schema,
            boolean readable,
            boolean writable,
            DataRecord initialValue
    ) {
        this(name, schema, readable, writable, initialValue, false, null);
    }

    public Variable(
            String name,
            DataSchema schema,
            boolean readable,
            boolean writable,
            DataRecord initialValue,
            boolean historyEnabled,
            Integer historyRetentionDays
    ) {
        this.name = name;
        this.schema = schema;
        this.readable = readable;
        this.writable = writable;
        this.historyEnabled = historyEnabled;
        this.historyRetentionDays = historyRetentionDays;
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

    /** Whether time-series samples are stored for this variable. */
    public boolean historyEnabled() {
        return historyEnabled;
    }

    /**
     * Per-variable retention in days; {@code null} uses the platform default from configuration.
     */
    public Optional<Integer> historyRetentionDays() {
        return Optional.ofNullable(historyRetentionDays);
    }

    public Variable withHistorySettings(boolean enabled, Integer retentionDays) {
        return withDefinition(readable, writable, enabled, retentionDays);
    }

    public Variable withDefinition(
            boolean readable,
            boolean writable,
            boolean historyEnabled,
            Integer historyRetentionDays
    ) {
        Variable copy = new Variable(
                name,
                schema,
                readable,
                writable,
                value.get(),
                historyEnabled,
                historyRetentionDays
        );
        copy.updatedAt = this.updatedAt;
        return copy;
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
