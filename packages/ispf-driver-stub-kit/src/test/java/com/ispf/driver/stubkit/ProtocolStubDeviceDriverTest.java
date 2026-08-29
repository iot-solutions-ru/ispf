package com.ispf.driver.stubkit;

import com.ispf.core.model.DataRecord;
import com.ispf.core.model.DataSchema;
import com.ispf.core.model.FieldType;
import com.ispf.core.object.ObjectType;
import com.ispf.core.object.PlatformObject;
import com.ispf.driver.DeviceDriver;
import com.ispf.driver.DriverException;
import com.ispf.driver.DriverMaturity;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ProtocolStubDeviceDriverTest {

    @Test
    void stubIsMarkedStubAndReadOnly() throws Exception {
        ProtocolStubDeviceDriver driver = new ProtocolStubDeviceDriver(
                "unit-test-stub",
                "Unit Test Stub Driver",
                "Unit test connectivity stub",
                1
        ) {};
        assertEquals("unit-test-stub", driver.metadata().id());
        assertEquals(DriverMaturity.STUB, driver.metadata().maturity());

        StubDriverObject driverObject = new StubDriverObject(Map.of(
                "host", "127.0.0.1",
                "port", "1",
                "timeoutMs", "200"
        ));
        driver.initialize(driverObject);
        driver.connect();
        driver.readPoints(Map.of("status", "connected"));
        assertEquals(false, driverObject.variables.get("status").firstRow().get("connected"));

        DriverException error = assertThrows(DriverException.class, () ->
                driver.writePoint("status", DataRecord.single(
                        DataSchema.builder("value").field("value", FieldType.STRING).build(),
                        Map.of("value", "1")
                )));
        assertTrue(error.getMessage().contains("read-only"));
        driver.disconnect();
    }

    private static final class StubDriverObject implements DeviceDriver.DriverObject {
        private final Map<String, String> configuration;
        private final Map<String, DataRecord> variables = new HashMap<>();

        StubDriverObject(Map<String, String> configuration) {
            this.configuration = configuration;
        }

        @Override
        public PlatformObject deviceObject() {
            return new PlatformObject(
                    "test-protocol-stub",
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
        }

        @Override
        public Map<String, String> configuration() {
            return configuration;
        }
    }
}
