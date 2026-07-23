package com.ispf.driver.wmi;

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
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

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

    @Test
    void readsRealWmiValueOnWindows() throws Exception {
        assumeTrue(isWindows(), "WMI happy path requires a Windows host with PowerShell");

        WmiDeviceDriver driver = new WmiDeviceDriver();
        StubDriverObject driverObject = new StubDriverObject(Map.of());
        driver.initialize(driverObject);
        driver.connect();
        assertTrue(driver.isConnected());

        driver.readPoints(Map.of("osCaption", "SELECT Caption FROM Win32_OperatingSystem"));

        Map<String, Object> row = driverObject.variables.get("osCaption").firstRow();
        assertEquals(Boolean.TRUE, row.get("supported"));
        assertEquals("ok", row.get("status"));
        assertFalse(((String) row.get("value")).isBlank());
    }

    @Test
    void readsRealWmiValueWithPropertySuffixOnWindows() throws Exception {
        assumeTrue(isWindows(), "WMI happy path requires a Windows host with PowerShell");

        WmiDeviceDriver driver = new WmiDeviceDriver();
        StubDriverObject driverObject = new StubDriverObject(Map.of());
        driver.initialize(driverObject);
        driver.connect();

        driver.readPoints(Map.of("computerName", "SELECT * FROM Win32_ComputerSystem:Name"));

        Map<String, Object> row = driverObject.variables.get("computerName").firstRow();
        assertEquals(Boolean.TRUE, row.get("supported"));
        assertEquals("ok", row.get("status"));
        assertFalse(((String) row.get("value")).isBlank());
    }

    @Test
    void reportsSupportedFlagMatchingHostPlatform() throws Exception {
        WmiDeviceDriver driver = new WmiDeviceDriver();
        StubDriverObject driverObject = new StubDriverObject(Map.of());
        driver.initialize(driverObject);
        driver.connect();
        assertTrue(driver.isConnected());

        driver.readPoints(Map.of("os", "SELECT Name FROM Win32_OperatingSystem"));

        DataRecord record = driverObject.variables.get("os");
        assertEquals("wmiValue", record.schema().name());
        assertEquals(1, record.rowCount());
        Map<String, Object> row = record.firstRow();
        assertTrue(row.containsKey("value"));
        assertTrue(row.containsKey("supported"));
        assertTrue(row.containsKey("status"));
        assertEquals(isWindows(), row.get("supported"));
        if (isWindows()) {
            assertEquals("ok", row.get("status"));
            assertFalse(((String) row.get("value")).isBlank());
        } else {
            assertEquals("", row.get("value"));
            assertTrue(((String) row.get("status")).startsWith("unsupported"));
        }
    }

    @Test
    void readPointsRequiresConnection() {
        WmiDeviceDriver driver = new WmiDeviceDriver();
        driver.initialize(new StubDriverObject(Map.of()));

        DriverException error = assertThrows(DriverException.class, () ->
                driver.readPoints(Map.of("os", "SELECT Name FROM Win32_OperatingSystem")));
        assertTrue(error.getMessage().contains("Not connected"));
    }

    @Test
    void writePointIsReadOnly() {
        WmiDeviceDriver driver = new WmiDeviceDriver();
        driver.initialize(new StubDriverObject(Map.of()));

        DriverException error = assertThrows(DriverException.class, () ->
                driver.writePoint("os", null));
        assertTrue(error.getMessage().contains("read-only"));
    }

    private static boolean isWindows() {
        return System.getProperty("os.name", "").toLowerCase().contains("windows");
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
        }

        @Override
        public Map<String, String> configuration() {
            return configuration;
        }
    }
}
