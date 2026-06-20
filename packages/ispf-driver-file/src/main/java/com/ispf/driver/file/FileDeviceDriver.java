package com.ispf.driver.file;

import com.ispf.core.model.DataRecord;
import com.ispf.core.model.DataSchema;
import com.ispf.core.model.FieldType;
import com.ispf.driver.DeviceDriver;
import com.ispf.driver.DriverException;
import com.ispf.driver.DriverMetadata;

import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * File driver — read-only file metadata and content preview.
 */
public class FileDeviceDriver implements DeviceDriver {

    private static final int PREVIEW_BYTES = 4096;

    private static final DataSchema FILE_SCHEMA = DataSchema.builder("fileInfo")
            .field("exists", FieldType.BOOLEAN)
            .field("size", FieldType.LONG)
            .field("lastModified", FieldType.LONG)
            .field("value", FieldType.STRING)
            .build();

    private static final DriverMetadata METADATA = new DriverMetadata(
            "file",
            "File Monitor Driver",
            "0.1.0",
            "Reads file existence, size, lastModified, and text preview (first 4KB)",
            "ISPF",
            Map.of(
                    "basePath", "",
                    "pollIntervalMs", "30000"
            )
    );

    private DriverObject driverObject;
    private String basePath = "";
    private final Map<String, String> points = new ConcurrentHashMap<>();
    private volatile boolean connected;

    @Override
    public DriverMetadata metadata() {
        return METADATA;
    }

    @Override
    public void initialize(DriverObject driverObject) {
        this.driverObject = driverObject;
        driverObject.configuration().forEach(this::applyConfig);
    }

    private void applyConfig(String key, String value) {
        if (value == null) {
            return;
        }
        if ("basePath".equals(key)) {
            basePath = value.trim();
        }
    }

    @Override
    public void connect() throws DriverException {
        connected = true;
        driverObject.log(DriverLogLevel.INFO, "File driver ready (basePath=" + (basePath.isEmpty() ? "." : basePath) + ")");
    }

    @Override
    public void disconnect() {
        connected = false;
    }

    @Override
    public boolean isConnected() {
        return connected;
    }

    @Override
    public void readPoints(Map<String, String> pointMappings) throws DriverException {
        if (!isConnected()) {
            throw new DriverException("Not connected");
        }
        points.clear();
        for (Map.Entry<String, String> entry : pointMappings.entrySet()) {
            String pathSpec = entry.getValue();
            if (pathSpec == null || pathSpec.isBlank()) {
                throw new DriverException("File path mapping is blank for " + entry.getKey());
            }
            points.put(entry.getKey(), pathSpec);
            driverObject.updateVariable(entry.getKey(), readFile(pathSpec));
        }
    }

    @Override
    public void writePoint(String pointId, DataRecord value) throws DriverException {
        throw new DriverException("File driver is read-only in v0.1");
    }

    private DataRecord readFile(String pathSpec) throws DriverException {
        try {
            Path path = resolvePath(pathSpec);
            boolean exists = Files.exists(path);
            if (!exists) {
                return DataRecord.single(FILE_SCHEMA, Map.of(
                        "exists", false,
                        "size", 0L,
                        "lastModified", 0L,
                        "value", ""
                ));
            }
            long size = Files.size(path);
            long lastModified = Files.getLastModifiedTime(path).toMillis();
            String preview = readPreview(path);
            return DataRecord.single(FILE_SCHEMA, Map.of(
                    "exists", true,
                    "size", size,
                    "lastModified", lastModified,
                    "value", preview
            ));
        } catch (IOException e) {
            throw new DriverException("File read failed for " + pathSpec, e);
        }
    }

    private Path resolvePath(String pathSpec) {
        Path path = Paths.get(pathSpec.trim());
        if (path.isAbsolute() || basePath.isEmpty()) {
            return path.normalize();
        }
        return Paths.get(basePath, pathSpec.trim()).normalize();
    }

    private static String readPreview(Path path) throws IOException {
        byte[] bytes = Files.readAllBytes(path);
        int length = Math.min(bytes.length, PREVIEW_BYTES);
        return new String(bytes, 0, length, detectCharset(bytes, length));
    }

    private static Charset detectCharset(byte[] bytes, int length) {
        if (length >= 3 && bytes[0] == (byte) 0xEF && bytes[1] == (byte) 0xBB && bytes[2] == (byte) 0xBF) {
            return StandardCharsets.UTF_8;
        }
        return StandardCharsets.UTF_8;
    }
}
