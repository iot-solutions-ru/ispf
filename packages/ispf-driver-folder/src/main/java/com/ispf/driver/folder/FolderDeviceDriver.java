package com.ispf.driver.folder;

import com.ispf.core.model.DataRecord;
import com.ispf.core.model.DataSchema;
import com.ispf.core.model.FieldType;
import com.ispf.driver.DeviceDriver;
import com.ispf.driver.DriverException;
import com.ispf.driver.DriverMetadata;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Stream;

/**
 * Folder driver — read-only folder statistics.
 */
public class FolderDeviceDriver implements DeviceDriver {

    private static final DataSchema FOLDER_SCHEMA = DataSchema.builder("folderInfo")
            .field("exists", FieldType.BOOLEAN)
            .field("fileCount", FieldType.INTEGER)
            .field("totalBytes", FieldType.LONG)
            .build();

    private static final DriverMetadata METADATA = new DriverMetadata(
            "folder",
            "Folder Monitor Driver",
            "0.1.0",
            "Reads folder existence, file count, and total byte size",
            "ISPF",
            Map.of(
                    "basePath", "",
                    "pollIntervalMs", "60000"
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
        driverObject.log(DriverLogLevel.INFO, "Folder driver ready");
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
                throw new DriverException("Folder path mapping is blank for " + entry.getKey());
            }
            points.put(entry.getKey(), pathSpec);
            driverObject.updateVariable(entry.getKey(), readFolder(pathSpec));
        }
    }

    @Override
    public void writePoint(String pointId, DataRecord value) throws DriverException {
        throw new DriverException("Folder driver is read-only in v0.1");
    }

    private DataRecord readFolder(String pathSpec) throws DriverException {
        try {
            Path path = resolvePath(pathSpec);
            if (!Files.exists(path) || !Files.isDirectory(path)) {
                return DataRecord.single(FOLDER_SCHEMA, Map.of(
                        "exists", false,
                        "fileCount", 0,
                        "totalBytes", 0L
                ));
            }
            long fileCount = 0;
            long totalBytes = 0;
            try (Stream<Path> walk = Files.walk(path)) {
                for (Path entry : walk.filter(Files::isRegularFile).toList()) {
                    fileCount++;
                    totalBytes += Files.size(entry);
                }
            }
            return DataRecord.single(FOLDER_SCHEMA, Map.of(
                    "exists", true,
                    "fileCount", (int) Math.min(fileCount, Integer.MAX_VALUE),
                    "totalBytes", totalBytes
            ));
        } catch (IOException e) {
            throw new DriverException("Folder read failed for " + pathSpec, e);
        }
    }

    private Path resolvePath(String pathSpec) {
        Path path = Paths.get(pathSpec.trim());
        if (path.isAbsolute() || basePath.isEmpty()) {
            return path.normalize();
        }
        return Paths.get(basePath, pathSpec.trim()).normalize();
    }
}
