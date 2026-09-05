package com.ispf.driver.fujisph;

import com.ispf.core.model.DataRecord;
import com.ispf.core.model.DataSchema;
import com.ispf.core.model.FieldType;
import com.ispf.core.object.ObjectType;
import com.ispf.core.object.PlatformObject;
import com.ispf.driver.DeviceDriver;
import com.ispf.driver.DriverMaturity;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Pack contract for STUB_LAB readiness (TCP probe + memory loopback).
 * Does not certify a protocol codec or field deployment.
 */
class FujiSphDeviceDriverContractTest {

    @Test
    void stubLabContractConnectReadWriteLoopback() throws Exception {
        FujiSphDeviceDriver driver = new FujiSphDeviceDriver();
        assertEquals("fuji-sph", driver.metadata().id());
        assertEquals(DriverMaturity.STUB, driver.metadata().maturity());
        assertEquals(Set.of("read", "write"), driver.metadata().capabilities());

        StubDriverObject driverObject = new StubDriverObject(Map.of(
                "host", "127.0.0.1",
                "port", "1",
                "timeoutMs", "200"
        ));
        driver.initialize(driverObject);
        driver.connect();
        assertTrue(driver.isConnected());

        driver.writePoint("lab", DataRecord.single(
                DataSchema.builder("value").field("value", FieldType.STRING).build(),
                Map.of("value", "stub-lab")
        ));
        driver.readPoints(Map.of("lab", "connected"));
        assertEquals("stub-lab", driverObject.variables.get("lab").firstRow().get("value"));
        assertEquals("loopback", driverObject.variables.get("lab").firstRow().get("mode"));

        driver.disconnect();
        assertTrue(!driver.isConnected());
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
                    "test-fuji-sph",
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
