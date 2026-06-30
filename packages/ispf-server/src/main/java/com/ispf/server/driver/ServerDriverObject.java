package com.ispf.server.driver;

import com.ispf.core.object.PlatformObject;
import com.ispf.core.model.DataRecord;
import com.ispf.driver.DeviceDriver;

import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.function.Consumer;

/**
 * Bridges a {@link DeviceDriver} to {@link com.ispf.server.object.ObjectManager}.
 */
public class ServerDriverObject implements DeviceDriver.DriverObject {

    private final PlatformObject deviceObject;
    private final Map<String, String> configuration;
    private final Consumer<VariableUpdate> variableUpdater;
    private final Consumer<LogEntry> logger;

    public record VariableUpdate(String path, String variableName, DataRecord value, boolean system, Instant observedAt) {
        public VariableUpdate(String path, String variableName, DataRecord value, boolean system) {
            this(path, variableName, value, system, null);
        }
    }

    public record LogEntry(DeviceDriver.DriverLogLevel level, String message) {
    }

    public ServerDriverObject(
            PlatformObject deviceObject,
            Map<String, String> configuration,
            Consumer<VariableUpdate> variableUpdater,
            Consumer<LogEntry> logger
    ) {
        this.deviceObject = deviceObject;
        this.configuration = configuration != null ? Map.copyOf(configuration) : Map.of();
        this.variableUpdater = variableUpdater;
        this.logger = logger;
    }

    @Override
    public PlatformObject deviceObject() {
        return deviceObject;
    }

    @Override
    public void updateVariable(String name, DataRecord value) {
        updateVariable(name, value, null);
    }

    @Override
    public void updateVariable(String name, DataRecord value, Instant observedAt) {
        variableUpdater.accept(new VariableUpdate(deviceObject.path(), name, value, false, observedAt));
    }

    @Override
    public Optional<DataRecord> getVariable(String name) {
        return deviceObject.getVariable(name).flatMap(com.ispf.core.object.Variable::value);
    }

    @Override
    public void log(DeviceDriver.DriverLogLevel level, String message) {
        logger.accept(new LogEntry(level, message));
    }

    @Override
    public Map<String, String> configuration() {
        return configuration;
    }
}
