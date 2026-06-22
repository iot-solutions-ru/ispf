package com.ispf.server.driver;

import com.ispf.core.model.DataRecord;
import com.ispf.core.model.DataSchema;
import com.ispf.core.model.FieldType;
import com.ispf.core.object.ObjectType;
import com.ispf.core.object.PlatformObject;
import com.ispf.driver.DeviceDriver;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

final class StubDriverObject implements DeviceDriver.DriverObject {

    private static final DataSchema STRING_VALUE = DataSchema.builder("stringValue")
            .field("value", FieldType.STRING)
            .field("raw", FieldType.STRING)
            .build();

    private final Map<String, String> configuration;
    private final Map<String, DataRecord> variables = new HashMap<>();
    private String lastStatus;

    StubDriverObject(Map<String, String> configuration) {
        this.configuration = configuration;
    }

    @Override
    public PlatformObject deviceObject() {
        return new PlatformObject(
                "test-device",
                "root.platform.devices.test",
                ObjectType.DEVICE,
                "Test",
                "",
                null
        );
    }

    @Override
    public void updateVariable(String name, DataRecord value) {
        variables.put(name, value);
        Object status = value.firstRow().get("status");
        if (status != null) {
            lastStatus = status.toString();
        }
    }

    @Override
    public Optional<DataRecord> getVariable(String name) {
        if (configuration.containsKey(name)) {
            String value = configuration.get(name);
            return Optional.of(DataRecord.single(STRING_VALUE, Map.of("value", value, "raw", value)));
        }
        return Optional.ofNullable(variables.get(name));
    }

    @Override
    public void log(DeviceDriver.DriverLogLevel level, String message) {
        // no-op
    }

    @Override
    public Map<String, String> configuration() {
        return configuration;
    }

    String lastStatus() {
        return lastStatus;
    }
}
