package com.ispf.driver.file;

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

class FileDeviceDriverTest {

    @TempDir
    Path tempDir;

    @Test
    void readsExistingFileViaBasePath() throws Exception {
        Files.writeString(tempDir.resolve("note.txt"), "hello ispf", StandardCharsets.UTF_8);

        StubDriverObject driverObject = new StubDriverObject(Map.of("basePath", tempDir.toString()));
        FileDeviceDriver driver = new FileDeviceDriver();
        driver.initialize(driverObject);
        driver.connect();
        driver.readPoints(Map.of("doc", "note.txt"));

        DataRecord record = driverObject.variables.get("doc");
        assertEquals(true, record.firstRow().get("exists"));
        assertEquals(10L, record.firstRow().get("size"));
        assertTrue((Long) record.firstRow().get("lastModified") > 0);
        assertEquals("hello ispf", record.firstRow().get("value"));
        driver.disconnect();
    }

    @Test
    void resolvesAbsolutePathWithoutBasePath() throws Exception {
        Path file = tempDir.resolve("absolute.txt");
        Files.writeString(file, "absolute-content", StandardCharsets.UTF_8);

        StubDriverObject driverObject = new StubDriverObject(Map.of());
        FileDeviceDriver driver = new FileDeviceDriver();
        driver.initialize(driverObject);
        driver.connect();
        driver.readPoints(Map.of("doc", file.toAbsolutePath().toString()));

        DataRecord record = driverObject.variables.get("doc");
        assertEquals(true, record.firstRow().get("exists"));
        assertEquals("absolute-content", record.firstRow().get("value"));
        driver.disconnect();
    }

    @Test
    void reportsMissingFileAsNotExisting() throws Exception {
        StubDriverObject driverObject = new StubDriverObject(Map.of("basePath", tempDir.toString()));
        FileDeviceDriver driver = new FileDeviceDriver();
        driver.initialize(driverObject);
        driver.connect();
        driver.readPoints(Map.of("missing", "no-such-file.txt"));

        DataRecord record = driverObject.variables.get("missing");
        assertEquals(false, record.firstRow().get("exists"));
        assertEquals(0L, record.firstRow().get("size"));
        assertEquals(0L, record.firstRow().get("lastModified"));
        assertEquals("", record.firstRow().get("value"));
        driver.disconnect();
    }

    @Test
    void truncatesPreviewAtFourKilobytes() throws Exception {
        byte[] big = new byte[5000];
        java.util.Arrays.fill(big, (byte) 'x');
        Files.write(tempDir.resolve("big.txt"), big);

        StubDriverObject driverObject = new StubDriverObject(Map.of("basePath", tempDir.toString()));
        FileDeviceDriver driver = new FileDeviceDriver();
        driver.initialize(driverObject);
        driver.connect();
        driver.readPoints(Map.of("big", "big.txt"));

        DataRecord record = driverObject.variables.get("big");
        assertEquals(5000L, record.firstRow().get("size"));
        assertEquals(4096, ((String) record.firstRow().get("value")).length());
        driver.disconnect();
    }

    @Test
    void rejectsBlankPathMapping() throws Exception {
        StubDriverObject driverObject = new StubDriverObject(Map.of());
        FileDeviceDriver driver = new FileDeviceDriver();
        driver.initialize(driverObject);
        driver.connect();

        assertThrows(DriverException.class, () -> driver.readPoints(Map.of("blank", "  ")));
        driver.disconnect();
    }

    @Test
    void readRequiresConnection() {
        FileDeviceDriver driver = new FileDeviceDriver();
        driver.initialize(new StubDriverObject(Map.of()));

        assertThrows(DriverException.class, () -> driver.readPoints(Map.of("doc", "note.txt")));
    }

    @Test
    void writeIsRejectedAsReadOnly() {
        FileDeviceDriver driver = new FileDeviceDriver();
        driver.initialize(new StubDriverObject(Map.of()));

        DriverException error = assertThrows(DriverException.class, () ->
                driver.writePoint("doc", DataRecord.single(
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
                    "test-file",
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
