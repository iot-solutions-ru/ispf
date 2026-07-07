package com.ispf.core.object;

import com.ispf.core.model.DataRecord;
import com.ispf.core.model.DataSchema;

import java.time.Instant;
import java.util.List;
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
    private final List<String> readRoles;
    private final List<String> writeRoles;
    private final AtomicReference<DataRecord> value = new AtomicReference<>();
    private volatile Instant updatedAt;

    public Variable(
            String name,
            DataSchema schema,
            boolean readable,
            boolean writable,
            DataRecord initialValue
    ) {
        this(name, schema, readable, writable, initialValue, false, null, List.of(), List.of());
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
        this(name, schema, readable, writable, initialValue, historyEnabled, historyRetentionDays, List.of(), List.of());
    }

    public Variable(
            String name,
            DataSchema schema,
            boolean readable,
            boolean writable,
            DataRecord initialValue,
            boolean historyEnabled,
            Integer historyRetentionDays,
            List<String> readRoles,
            List<String> writeRoles
    ) {
        this.name = name;
        this.schema = schema;
        this.readable = readable;
        this.writable = writable;
        this.historyEnabled = historyEnabled;
        this.historyRetentionDays = historyRetentionDays;
        this.readRoles = readRoles != null ? List.copyOf(readRoles) : List.of();
        this.writeRoles = writeRoles != null ? List.copyOf(writeRoles) : List.of();
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

    /** Role names allowed to read this variable; empty = inherit object ACL. */
    public List<String> readRoles() {
        return readRoles;
    }

    /** Role names allowed to write this variable; empty = inherit object ACL. */
    public List<String> writeRoles() {
        return writeRoles;
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
        return withDefinition(readable, writable, historyEnabled, historyRetentionDays, readRoles, writeRoles);
    }

    public Variable withDefinition(
            boolean readable,
            boolean writable,
            boolean historyEnabled,
            Integer historyRetentionDays,
            List<String> readRoles,
            List<String> writeRoles
    ) {
        Variable copy = new Variable(
                name,
                schema,
                readable,
                writable,
                value.get(),
                historyEnabled,
                historyRetentionDays,
                readRoles,
                writeRoles
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
