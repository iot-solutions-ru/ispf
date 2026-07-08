package com.ispf.driver.jsonpoller;

import com.ispf.core.model.DataRecord;
import com.ispf.core.object.ObjectType;
import com.ispf.core.object.PlatformObject;
import com.ispf.driver.DeviceDriver;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class JsonPollerDeviceDriverTest {

    @Test
    void readReturnsStubJsonForMappedPath() throws Exception {
        StubDriverObject driverObject = new StubDriverObject(Map.of("baseUrl", "http://127.0.0.1:9090"));
        JsonPollerDeviceDriver driver = new JsonPollerDeviceDriver();
        driver.initialize(driverObject);
        driver.connect();
        driver.readPoints(Map.of("temperature", "jsonPath:$.data.temp"));

        DataRecord record = driverObject.variables.get("temperature");
        assertEquals("$.data.temp", record.firstRow().get("jsonPath"));
        assertEquals(true, record.firstRow().get("connected"));
        driver.disconnect();
    }

    @Test
    void rejectsInvalidMapping() {
        assertThrows(IllegalArgumentException.class, () -> JsonPollerPoint.parse("bad"));
    }

    private static final class StubDriverObject implements DeviceDriver.DriverObject {
        private final Map<String, String> config;
        private final Map<String, DataRecord> variables = new HashMap<>();

        private StubDriverObject(Map<String, String> config) {
            this.config = config;
        }

        @Override
        public PlatformObject deviceObject() {
            return new PlatformObject("json-poller", "root.platform.devices.json-poller", ObjectType.DEVICE, "JSON Poller", "", null);
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
            return config;
        }
    }
}
