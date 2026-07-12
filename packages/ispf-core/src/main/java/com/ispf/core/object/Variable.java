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
    private final HistorySampleMode historySampleMode;
    private final boolean includePreviousValueInEvent;
    private final VariableStorageMode storageMode;
    /** {@code null} or blank = inherit device {@code telemetryPublishMode}. */
    private final String telemetryPublishMode;
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
        this(name, schema, readable, writable, initialValue, false, null, null, List.of(), List.of());
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
        this(name, schema, readable, writable, initialValue, historyEnabled, historyRetentionDays, null, List.of(), List.of());
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
        this(name, schema, readable, writable, initialValue, historyEnabled, historyRetentionDays, null, readRoles, writeRoles);
    }

    public Variable(
            String name,
            DataSchema schema,
            boolean readable,
            boolean writable,
            DataRecord initialValue,
            boolean historyEnabled,
            Integer historyRetentionDays,
            String telemetryPublishMode,
            List<String> readRoles,
            List<String> writeRoles
    ) {
        this(
                name,
                schema,
                readable,
                writable,
                initialValue,
                historyEnabled,
                historyRetentionDays,
                HistorySampleMode.CHANGES_ONLY,
                false,
                VariableStorageMode.PERSISTENT,
                telemetryPublishMode,
                readRoles,
                writeRoles
        );
    }

    public Variable(
            String name,
            DataSchema schema,
            boolean readable,
            boolean writable,
            DataRecord initialValue,
            boolean historyEnabled,
            Integer historyRetentionDays,
            HistorySampleMode historySampleMode,
            boolean includePreviousValueInEvent,
            VariableStorageMode storageMode,
            String telemetryPublishMode,
            List<String> readRoles,
            List<String> writeRoles
    ) {
        this.name = name;
        this.schema = schema;
        this.readable = readable;
        this.writable = writable;
        this.historyEnabled = historyEnabled;
        this.historyRetentionDays = historyRetentionDays;
        this.historySampleMode = historySampleMode != null ? historySampleMode : HistorySampleMode.CHANGES_ONLY;
        this.includePreviousValueInEvent = includePreviousValueInEvent;
        this.storageMode = storageMode != null ? storageMode : VariableStorageMode.PERSISTENT;
        this.telemetryPublishMode = normalizeTelemetryPublishMode(telemetryPublishMode);
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

    /** Historian writes on value change only, or on every accepted update. */
    public HistorySampleMode historySampleMode() {
        return historySampleMode;
    }

    /** When true, variable update events may carry {@code previousValue} alongside the new value. */
    public boolean includePreviousValueInEvent() {
        return includePreviousValueInEvent;
    }

    /** Whether live value is persisted to the config database. */
    public VariableStorageMode storageMode() {
        return storageMode;
    }

    /**
     * Per-variable telemetry publish mode override; empty = inherit the bound driver's default.
     */
    public Optional<String> telemetryPublishModeOverride() {
        return Optional.ofNullable(telemetryPublishMode);
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
        return withPolicySettings(
                enabled,
                retentionDays,
                historySampleMode,
                includePreviousValueInEvent,
                storageMode,
                telemetryPublishMode
        );
    }

    public Variable withStorageSettings(
            boolean historyEnabled,
            Integer historyRetentionDays,
            String telemetryPublishMode
    ) {
        return withPolicySettings(
                historyEnabled,
                historyRetentionDays,
                historySampleMode,
                includePreviousValueInEvent,
                storageMode,
                telemetryPublishMode
        );
    }

    public Variable withPolicySettings(
            boolean historyEnabled,
            Integer historyRetentionDays,
            HistorySampleMode historySampleMode,
            boolean includePreviousValueInEvent,
            VariableStorageMode storageMode,
            String telemetryPublishMode
    ) {
        return withDefinition(
                readable,
                writable,
                historyEnabled,
                historyRetentionDays,
                historySampleMode,
                includePreviousValueInEvent,
                storageMode,
                telemetryPublishMode,
                readRoles,
                writeRoles
        );
    }

    public Variable withDefinition(
            boolean readable,
            boolean writable,
            boolean historyEnabled,
            Integer historyRetentionDays
    ) {
        return withDefinition(
                readable,
                writable,
                historyEnabled,
                historyRetentionDays,
                telemetryPublishMode,
                readRoles,
                writeRoles
        );
    }

    public Variable withDefinition(
            boolean readable,
            boolean writable,
            boolean historyEnabled,
            Integer historyRetentionDays,
            List<String> readRoles,
            List<String> writeRoles
    ) {
        return withDefinition(
                readable,
                writable,
                historyEnabled,
                historyRetentionDays,
                telemetryPublishMode,
                readRoles,
                writeRoles
        );
    }

    public Variable withDefinition(
            boolean readable,
            boolean writable,
            boolean historyEnabled,
            Integer historyRetentionDays,
            String telemetryPublishMode,
            List<String> readRoles,
            List<String> writeRoles
    ) {
        return withDefinition(
                readable,
                writable,
                historyEnabled,
                historyRetentionDays,
                historySampleMode,
                includePreviousValueInEvent,
                storageMode,
                telemetryPublishMode,
                readRoles,
                writeRoles
        );
    }

    public Variable withDefinition(
            boolean readable,
            boolean writable,
            boolean historyEnabled,
            Integer historyRetentionDays,
            HistorySampleMode historySampleMode,
            boolean includePreviousValueInEvent,
            VariableStorageMode storageMode,
            String telemetryPublishMode,
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
                historySampleMode,
                includePreviousValueInEvent,
                storageMode,
                telemetryPublishMode,
                readRoles,
                writeRoles
        );
        copy.updatedAt = this.updatedAt;
        return copy;
    }

    private static String normalizeTelemetryPublishMode(String raw) {
        if (raw == null || raw.isBlank() || "INHERIT".equalsIgnoreCase(raw.trim())) {
            return null;
        }
        return raw.trim().toUpperCase();
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
