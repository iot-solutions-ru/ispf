package com.ispf.driver.folder;

import com.ispf.core.model.DataRecord;
import com.ispf.core.object.ObjectType;
import com.ispf.core.object.PlatformObject;
import com.ispf.driver.DeviceDriver;
import com.ispf.driver.DriverException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FolderDeviceDriverTest {

    @TempDir
    Path tempDir;

    @Test
    void countsFilesRecursively() throws Exception {
        Files.writeString(tempDir.resolve("a.txt"), "abc", StandardCharsets.UTF_8);
        Path sub = Files.createDirectory(tempDir.resolve("sub"));
        Files.writeString(sub.resolve("b.txt"), "abcde", StandardCharsets.UTF_8);
        Path deep = Files.createDirectory(sub.resolve("deep"));
        Files.writeString(deep.resolve("c.txt"), "xy", StandardCharsets.UTF_8);
        Files.createDirectory(tempDir.resolve("empty-dir"));

        StubDriverObject driverObject = new StubDriverObject(Map.of("basePath", tempDir.toString()));
        FolderDeviceDriver driver = new FolderDeviceDriver();
        driver.initialize(driverObject);
        driver.connect();
        driver.readPoints(Map.of("stats", "."));

        DataRecord record = driverObject.variables.get("stats");
        assertEquals(true, record.firstRow().get("exists"));
        assertEquals(3, record.firstRow().get("fileCount"));
        assertEquals(10L, record.firstRow().get("totalBytes"));
        driver.disconnect();
    }

    @Test
    void resolvesRelativePathAgainstBasePath() throws Exception {
        Files.writeString(tempDir.resolve("root.txt"), "ignored", StandardCharsets.UTF_8);
        Path sub = Files.createDirectory(tempDir.resolve("sub"));
        Files.writeString(sub.resolve("b.txt"), "abcde", StandardCharsets.UTF_8);
        Files.writeString(sub.resolve("c.txt"), "xy", StandardCharsets.UTF_8);

        StubDriverObject driverObject = new StubDriverObject(Map.of("basePath", tempDir.toString()));
        FolderDeviceDriver driver = new FolderDeviceDriver();
        driver.initialize(driverObject);
        driver.connect();
        driver.readPoints(Map.of("stats", "sub"));

        DataRecord record = driverObject.variables.get("stats");
        assertEquals(true, record.firstRow().get("exists"));
        assertEquals(2, record.firstRow().get("fileCount"));
        assertEquals(7L, record.firstRow().get("totalBytes"));
        driver.disconnect();
    }

    @Test
    void reportsMissingFolderAsNotExisting() throws Exception {
        StubDriverObject driverObject = new StubDriverObject(Map.of("basePath", tempDir.toString()));
        FolderDeviceDriver driver = new FolderDeviceDriver();
        driver.initialize(driverObject);
        driver.connect();
        driver.readPoints(Map.of("stats", "no-such-folder"));

        DataRecord record = driverObject.variables.get("stats");
        assertEquals(false, record.firstRow().get("exists"));
        assertEquals(0, record.firstRow().get("fileCount"));
        assertEquals(0L, record.firstRow().get("totalBytes"));
        driver.disconnect();
    }

    @Test
    void reportsRegularFileAsNotExisting() throws Exception {
        Files.writeString(tempDir.resolve("file.txt"), "abc", StandardCharsets.UTF_8);

        StubDriverObject driverObject = new StubDriverObject(Map.of("basePath", tempDir.toString()));
        FolderDeviceDriver driver = new FolderDeviceDriver();
        driver.initialize(driverObject);
        driver.connect();
        driver.readPoints(Map.of("stats", "file.txt"));

        DataRecord record = driverObject.variables.get("stats");
        assertEquals(false, record.firstRow().get("exists"));
        assertEquals(0, record.firstRow().get("fileCount"));
        assertEquals(0L, record.firstRow().get("totalBytes"));
        driver.disconnect();
    }

    @Test
    void readRequiresConnection() {
        FolderDeviceDriver driver = new FolderDeviceDriver();
        driver.initialize(new StubDriverObject(Map.of()));

        assertThrows(DriverException.class, () -> driver.readPoints(Map.of("stats", ".")));
    }

    @Test
    void writeIsRejectedAsReadOnly() {
        FolderDeviceDriver driver = new FolderDeviceDriver();
        driver.initialize(new StubDriverObject(Map.of()));

        DriverException error = assertThrows(DriverException.class, () ->
                driver.writePoint("stats", DataRecord.single(
                        com.ispf.core.model.DataSchema.builder("value")
                                .field("value", com.ispf.core.model.FieldType.STRING)
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
                    "test-folder",
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
