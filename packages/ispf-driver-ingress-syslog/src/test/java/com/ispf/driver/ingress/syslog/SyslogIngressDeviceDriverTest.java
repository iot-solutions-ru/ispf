package com.ispf.driver.ingress.syslog;

import com.ispf.core.object.ObjectType;
import com.ispf.core.object.PlatformObject;
import com.ispf.driver.DeviceDriver;
import com.ispf.driver.DriverException;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

import static org.junit.jupiter.api.Assertions.assertTrue;

class SyslogIngressDeviceDriverTest {

    @Test
    void connectsWithConfiguredPort() throws Exception {
        StubDriverObject driverObject = new StubDriverObject(Map.of("port", "0"));
        SyslogIngressDeviceDriver driver = new SyslogIngressDeviceDriver();
        driver.initialize(driverObject);
        driver.connect();
        assertTrue(driver.isConnected());
        driver.disconnect();
    }

    @Test
    void readPointsRequiresConnection() {
        SyslogIngressDeviceDriver driver = new SyslogIngressDeviceDriver();
        driver.initialize(new StubDriverObject(Map.of("port", "0")));
        org.junit.jupiter.api.Assertions.assertThrows(
                DriverException.class,
                () -> driver.readPoints(Map.of("stats", "syslog"))
        );
    }

    private static final class StubDriverObject implements DeviceDriver.DriverObject {
        private final Map<String, String> config;

        private StubDriverObject(Map<String, String> config) {
            this.config = config;
        }

        @Override
        public PlatformObject deviceObject() {
            return new PlatformObject(
                    "ingress-syslog-test",
                    "root.platform.devices.itm.ingress.syslog",
                    ObjectType.DEVICE,
                    "Syslog",
                    "",
                    null
            );
        }

        @Override
        public void updateVariable(String name, com.ispf.core.model.DataRecord value) {
        }

        @Override
        public Optional<com.ispf.core.model.DataRecord> getVariable(String name) {
            return Optional.empty();
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
