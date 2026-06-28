package com.ispf.driver.modbusrtu;

import com.ispf.core.model.DataRecord;
import com.ispf.core.model.DataSchema;
import com.ispf.core.model.FieldType;
import com.ispf.core.object.ObjectType;
import com.ispf.core.object.PlatformObject;
import com.ispf.driver.DeviceDriver;
import com.ispf.driver.DriverException;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ModbusRtuDeviceDriverTest {

    private static final DataSchema REGISTER_SCHEMA = DataSchema.builder("modbusRegister")
            .field("value", FieldType.DOUBLE)
            .field("raw", FieldType.LONG)
            .build();

    @Test
    void rejectsWriteWhenNotConnected() {
        ModbusRtuDeviceDriver driver = new ModbusRtuDeviceDriver();
        driver.initialize(new StubDriverObject(Map.of("serialPort", "COM_TEST", "timeoutMs", "1000")));

        DriverException error = assertThrows(DriverException.class, () ->
                driver.writePoint("setpoint", DataRecord.single(REGISTER_SCHEMA, Map.of("raw", 1L, "value", 1.0))));
        assertTrue(error.getMessage().contains("Not connected"));
    }

    @Test
    void rejectsUnknownPointWhenConnected() {
        ModbusRtuDeviceDriver driver = new ConnectedModbusRtuDeviceDriver();
        driver.initialize(new StubDriverObject(Map.of("serialPort", "COM_TEST", "timeoutMs", "1000")));

        DriverException error = assertThrows(DriverException.class, () ->
                driver.writePoint("setpoint", DataRecord.single(REGISTER_SCHEMA, Map.of("raw", 1L, "value", 1.0))));
        assertTrue(error.getMessage().contains("Unknown point"));
    }

    /** Avoids requiring a real serial port while exercising write guard rails. */
    private static final class ConnectedModbusRtuDeviceDriver extends ModbusRtuDeviceDriver {
        @Override
        public boolean isConnected() {
            return true;
        }
    }

    private static final class StubDriverObject implements DeviceDriver.DriverObject {

        private static final DataSchema STRING_VALUE = DataSchema.builder("stringValue")
                .field("value", FieldType.STRING)
                .field("raw", FieldType.STRING)
                .build();

        private final Map<String, String> configuration;

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
        }

        @Override
        public Optional<DataRecord> getVariable(String name) {
            if (configuration.containsKey(name)) {
                String value = configuration.get(name);
                return Optional.of(DataRecord.single(STRING_VALUE, Map.of("value", value, "raw", value)));
            }
            return Optional.empty();
        }

        @Override
        public void log(DeviceDriver.DriverLogLevel level, String message) {
        }

        @Override
        public Map<String, String> configuration() {
            return configuration;
        }
    }
}
