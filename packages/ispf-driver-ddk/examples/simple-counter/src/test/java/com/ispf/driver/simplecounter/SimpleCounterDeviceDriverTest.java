package com.ispf.driver.simplecounter;

import com.ispf.core.model.DataRecord;
import com.ispf.core.object.ObjectType;
import com.ispf.core.object.PlatformObject;
import com.ispf.driver.DeviceDriver;
import com.ispf.driver.DriverException;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;

class SimpleCounterDeviceDriverTest {

    @Test
    void incrementsCounterOnEachRead() throws Exception {
        StubDriverObject driverObject = new StubDriverObject();
        SimpleCounterDeviceDriver driver = new SimpleCounterDeviceDriver();
        driver.initialize(driverObject);
        driver.connect();
        driver.readPoints(Map.of("events", "counter:main"));
        driver.readPoints(Map.of("events", "counter:main"));

        assertEquals(2L, driverObject.variables.get("events").firstRow().get("count"));
        driver.disconnect();
    }

    private static final class StubDriverObject implements DeviceDriver.DriverObject {
        private final Map<String, DataRecord> variables = new HashMap<>();

        @Override
        public PlatformObject deviceObject() {
            return new PlatformObject("simple-counter", "root.platform.devices.simple-counter", ObjectType.DEVICE, "Counter", "", null);
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
            return Map.of();
        }
    }
}
