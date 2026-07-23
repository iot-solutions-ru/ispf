package com.ispf.driver.icmp;

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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class IcmpDeviceDriverTest {

    @Test
    void pollsLoopbackHost() throws Exception {
        StubDriverObject driverObject = new StubDriverObject(Map.of(
                "host", "127.0.0.1",
                "timeoutMs", "2000"
        ));
        IcmpDeviceDriver driver = new IcmpDeviceDriver();
        driver.initialize(driverObject);
        driver.connect();
        driver.readPoints(Map.of(
                "gateway", "127.0.0.1",
                "fallback", "" // blank mapping uses the configured default host
        ));

        DataRecord gateway = driverObject.variables.get("gateway");
        assertEquals(Boolean.TRUE, gateway.firstRow().get("reachable"));
        assertEquals("127.0.0.1", gateway.firstRow().get("host"));
        assertTrue((Double) gateway.firstRow().get("latencyMs") >= 0.0);

        DataRecord fallback = driverObject.variables.get("fallback");
        assertEquals(Boolean.TRUE, fallback.firstRow().get("reachable"));
        assertEquals("127.0.0.1", fallback.firstRow().get("host"));
        driver.disconnect();
    }

    @Test
    void writeIsReadOnly() {
        IcmpDeviceDriver driver = new IcmpDeviceDriver();
        driver.initialize(new StubDriverObject(Map.of()));

        DriverException error = assertThrows(DriverException.class, () ->
                driver.writePoint("gateway", DataRecord.single(
                        DataSchema.builder("value")
                                .field("value", FieldType.STRING)
                                .build(),
                        Map.of("value", "1")
                )));
        assertTrue(error.getMessage().contains("read-only"));
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
                    "test-icmp",
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
