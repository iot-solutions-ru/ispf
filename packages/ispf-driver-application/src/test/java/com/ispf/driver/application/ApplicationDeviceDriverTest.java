package com.ispf.driver.application;

import com.ispf.core.model.DataRecord;
import com.ispf.core.object.ObjectType;
import com.ispf.core.object.PlatformObject;
import com.ispf.driver.DeviceDriver;
import com.ispf.driver.DriverException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ApplicationDeviceDriverTest {

    @TempDir
    Path tempDir;

    @Test
    void executesEchoCommand() throws Exception {
        StubDriverObject driverObject = new StubDriverObject(Map.of("timeoutMs", "10000"));
        ApplicationDeviceDriver driver = new ApplicationDeviceDriver();
        driver.initialize(driverObject);
        driver.connect();
        driver.readPoints(Map.of("greeting", "echo hello"));

        DataRecord record = driverObject.variables.get("greeting");
        assertEquals(0, record.firstRow().get("exitCode"));
        assertEquals("hello", record.firstRow().get("value"));
        assertEquals("", record.firstRow().get("stderr"));
        driver.disconnect();
    }

    @Test
    void capturesNonZeroExitCodeAndStderr() throws Exception {
        StubDriverObject driverObject = new StubDriverObject(Map.of("timeoutMs", "10000"));
        ApplicationDeviceDriver driver = new ApplicationDeviceDriver();
        driver.initialize(driverObject);
        driver.connect();
        driver.readPoints(Map.of("probe", "echo err 1>&2 && exit 3"));

        DataRecord record = driverObject.variables.get("probe");
        assertEquals(3, record.firstRow().get("exitCode"));
        assertEquals("err", record.firstRow().get("stderr"));
        driver.disconnect();
    }

    @Test
    void runsCommandInConfiguredWorkingDir() throws Exception {
        String command = isWindows() ? "cd" : "pwd";

        StubDriverObject driverObject = new StubDriverObject(Map.of(
                "workingDir", tempDir.toString(),
                "timeoutMs", "10000"
        ));
        ApplicationDeviceDriver driver = new ApplicationDeviceDriver();
        driver.initialize(driverObject);
        driver.connect();
        driver.readPoints(Map.of("cwd", command));

        DataRecord record = driverObject.variables.get("cwd");
        assertEquals(0, record.firstRow().get("exitCode"));
        Path reported = Path.of((String) record.firstRow().get("value")).toAbsolutePath().normalize();
        assertEquals(tempDir.toAbsolutePath().normalize(), reported);
        driver.disconnect();
    }

    @Test
    void rejectsBlankCommandMapping() throws Exception {
        StubDriverObject driverObject = new StubDriverObject(Map.of());
        ApplicationDeviceDriver driver = new ApplicationDeviceDriver();
        driver.initialize(driverObject);
        driver.connect();

        assertThrows(DriverException.class, () -> driver.readPoints(Map.of("blank", "  ")));
        driver.disconnect();
    }

    @Test
    void readRequiresConnection() {
        ApplicationDeviceDriver driver = new ApplicationDeviceDriver();
        driver.initialize(new StubDriverObject(Map.of()));

        assertThrows(DriverException.class, () -> driver.readPoints(Map.of("greeting", "echo hello")));
    }

    @Test
    void writeIsRejectedAsReadOnly() {
        ApplicationDeviceDriver driver = new ApplicationDeviceDriver();
        driver.initialize(new StubDriverObject(Map.of()));

        DriverException error = assertThrows(DriverException.class, () ->
                driver.writePoint("greeting", DataRecord.single(
                        com.ispf.core.model.DataSchema.builder("value")
                                .field("value", com.ispf.core.model.FieldType.STRING)
                                .build(),
                        Map.of("value", "1")
                )));
        assertTrue(error.getMessage().contains("read-only"));
    }

    private static boolean isWindows() {
        return System.getProperty("os.name", "").toLowerCase().contains("win");
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
                    "test-application",
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
