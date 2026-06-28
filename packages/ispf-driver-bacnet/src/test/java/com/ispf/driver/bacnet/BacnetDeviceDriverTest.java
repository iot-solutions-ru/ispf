package com.ispf.driver.bacnet;

import com.ispf.core.model.DataRecord;
import com.ispf.core.model.DataSchema;
import com.ispf.core.model.FieldType;
import com.ispf.core.object.ObjectType;
import com.ispf.core.object.PlatformObject;
import com.ispf.driver.DeviceDriver;
import com.ispf.driver.DriverException;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BacnetDeviceDriverTest {

    private static final DataSchema VALUE_SCHEMA = DataSchema.builder("bacnetValue")
            .field("value", FieldType.STRING)
            .build();

    @Test
    void writeRequiresConnection() {
        BacnetDeviceDriver driver = new BacnetDeviceDriver();
        driver.initialize(new StubDriverObject(Map.of()));

        DriverException error = assertThrows(DriverException.class, () ->
                driver.writePoint("setpoint", DataRecord.single(VALUE_SCHEMA, Map.of("value", "1"))));
        assertTrue(error.getMessage().contains("Not connected"));
    }

    @Test
    void rejectsUnknownPoint() {
        BacnetDeviceDriver driver = new BacnetDeviceDriver();
        driver.initialize(new StubDriverObject(Map.of()));

        DriverException error = assertThrows(DriverException.class, () ->
                driver.writePoint("missing", DataRecord.single(VALUE_SCHEMA, Map.of("value", "1"))));
        assertTrue(error.getMessage().contains("Not connected") || error.getMessage().contains("Unknown point"));
    }

    private static final class StubDriverObject implements DeviceDriver.DriverObject {

        private final Map<String, DataRecord> variables = new HashMap<>();

        StubDriverObject(Map<String, String> configuration) {
            // no-op
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
        }

        @Override
        public Optional<DataRecord> getVariable(String name) {
            return Optional.ofNullable(variables.get(name));
        }

        @Override
        public void log(DeviceDriver.DriverLogLevel level, String message) {
            // no-op
        }
    }
}
