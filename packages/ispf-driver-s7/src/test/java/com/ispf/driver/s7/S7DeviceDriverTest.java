package com.ispf.driver.s7;

import com.github.s7connector.api.DaveArea;
import com.github.s7connector.api.S7Connector;
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
import java.util.concurrent.ConcurrentHashMap;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class S7DeviceDriverTest {

    private static final DataSchema VALUE_SCHEMA = DataSchema.builder("s7Value")
            .field("value", FieldType.DOUBLE)
            .field("raw", FieldType.LONG)
            .build();

    @Test
    void writeRealUpdatesMemoryAndVariable() throws Exception {
        MemoryS7Connector connector = new MemoryS7Connector();
        StubDriverObject driverObject = new StubDriverObject(Map.of("host", "127.0.0.1", "timeoutMs", "3000"));
        S7DeviceDriver driver = new S7DeviceDriver();
        driver.initialize(driverObject);
        driver.attachConnector(connector);
        driver.readPoints(Map.of("setpoint", "DB:1:0:REAL"));

        driver.writePoint("setpoint", DataRecord.single(VALUE_SCHEMA, Map.of("raw", 250L, "value", 250.0)));

        assertEquals(250.0, driverObject.variables.get("setpoint").firstRow().get("value"));
        DataRecord readBack = S7DeviceDriver.decodeValue(
                S7Point.S7DataType.REAL,
                connector.read(DaveArea.DB, 1, 4, 0));
        assertEquals(250.0, readBack.firstRow().get("value"));
    }

    @Test
    void rejectsWriteWhenNotConnected() {
        StubDriverObject driverObject = new StubDriverObject(Map.of("host", "127.0.0.1", "timeoutMs", "3000"));
        S7DeviceDriver driver = new S7DeviceDriver();
        driver.initialize(driverObject);

        DriverException error = assertThrows(DriverException.class, () ->
                driver.writePoint("setpoint", DataRecord.single(VALUE_SCHEMA, Map.of("raw", 1L, "value", 1.0))));
        assertTrue(error.getMessage().contains("Not connected"));
    }

    @Test
    void rejectsUnknownPointWhenConnected() {
        StubDriverObject driverObject = new StubDriverObject(Map.of("host", "127.0.0.1", "timeoutMs", "3000"));
        S7DeviceDriver driver = new S7DeviceDriver();
        driver.initialize(driverObject);
        driver.attachConnector(new MemoryS7Connector());

        DriverException error = assertThrows(DriverException.class, () ->
                driver.writePoint("setpoint", DataRecord.single(VALUE_SCHEMA, Map.of("raw", 1L, "value", 1.0))));
        assertTrue(error.getMessage().contains("Unknown point"));
    }

    private static final class MemoryS7Connector implements S7Connector {

        private final Map<String, byte[]> storage = new ConcurrentHashMap<>();

        @Override
        public byte[] read(DaveArea area, int dbNumber, int length, int offset) {
            byte[] stored = storage.get(key(area, dbNumber, offset));
            if (stored == null) {
                return new byte[length];
            }
            byte[] copy = new byte[length];
            System.arraycopy(stored, 0, copy, 0, Math.min(length, stored.length));
            return copy;
        }

        @Override
        public void write(DaveArea area, int dbNumber, int offset, byte[] data) {
            storage.put(key(area, dbNumber, offset), data.clone());
        }

        @Override
        public void close() {
        }

        private static String key(DaveArea area, int dbNumber, int offset) {
            return area + ":" + dbNumber + ":" + offset;
        }
    }

    private static final class StubDriverObject implements DeviceDriver.DriverObject {

        private static final DataSchema STRING_VALUE = DataSchema.builder("stringValue")
                .field("value", FieldType.STRING)
                .field("raw", FieldType.STRING)
                .build();

        private final Map<String, String> configuration;
        private final Map<String, DataRecord> variables = new HashMap<>();

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
        }

        @Override
        public Map<String, String> configuration() {
            return configuration;
        }
    }
}
