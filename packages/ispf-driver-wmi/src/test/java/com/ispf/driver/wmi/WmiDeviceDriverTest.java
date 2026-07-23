package com.ispf.driver.wmi;

import com.ispf.core.model.DataRecord;
import com.ispf.core.object.ObjectType;
import com.ispf.core.object.PlatformObject;
import com.ispf.driver.DeviceDriver;
import com.ispf.driver.DriverException;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WmiDeviceDriverTest {

    @Test
    void rejectsPropertyWithInjectionCharacters() throws Exception {
        WmiDeviceDriver driver = new WmiDeviceDriver();
        driver.initialize(new StubDriverObject(Map.of()));
        driver.connect();

        DriverException error = assertThrows(DriverException.class, () ->
                driver.readPoints(Map.of("os", "SELECT Name; evil FROM Win32_OperatingSystem")));
        assertTrue(error.getMessage().contains("Invalid WMI property name"));
    }

    private static final class StubDriverObject implements DeviceDriver.DriverObject {

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
