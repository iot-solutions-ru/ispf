package com.ispf.driver.application;

import com.ispf.core.model.DataRecord;
import com.ispf.core.model.DataSchema;
import com.ispf.core.model.FieldType;
import com.ispf.driver.DeviceDriver;
import com.ispf.driver.DriverException;
import com.ispf.driver.DriverMetadata;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

/**
 * Local application driver — executes shell commands via ProcessBuilder.
 */
public class ApplicationDeviceDriver implements DeviceDriver {

    private static final DataSchema COMMAND_SCHEMA = DataSchema.builder("applicationCommand")
            .field("value", FieldType.STRING)
            .field("exitCode", FieldType.INTEGER)
            .field("stderr", FieldType.STRING)
            .build();

    private static final DriverMetadata METADATA = new DriverMetadata(
            "application",
            "Local Application Driver",
            "0.1.0",
            "Executes local shell commands and maps stdout/stderr to ISPF variables",
            "ISPF",
            Map.of(
                    "workingDir", "",
                    "timeoutMs", "10000",
                    "pollIntervalMs", "60000"
            )
    );

    private DriverObject driverObject;
    private String workingDir = "";
    private int timeoutMs = 10_000;
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
        if (value == null || value.isBlank()) {
            return;
        }
        switch (key) {
            case "workingDir" -> workingDir = value.trim();
            case "timeoutMs" -> timeoutMs = Integer.parseInt(value.trim());
            default -> { }
        }
    }

    @Override
    public void connect() throws DriverException {
        connected = true;
        driverObject.log(DriverLogLevel.INFO, "Application driver ready");
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
            String command = entry.getValue();
            if (command == null || command.isBlank()) {
                throw new DriverException("Command mapping is blank for " + entry.getKey());
            }
            points.put(entry.getKey(), command);
            driverObject.updateVariable(entry.getKey(), execute(command));
        }
    }

    @Override
    public void writePoint(String pointId, DataRecord value) throws DriverException {
        throw new DriverException("Application driver is read-only in v0.1");
    }

    private DataRecord execute(String command) throws DriverException {
        try {
            ProcessBuilder builder = isWindows()
                    ? new ProcessBuilder("cmd.exe", "/c", command)
                    : new ProcessBuilder("sh", "-c", command);
            if (!workingDir.isEmpty()) {
                builder.directory(Path.of(workingDir).toFile());
            } else {
                Path cwd = Paths.get("").toAbsolutePath();
                builder.directory(cwd.toFile());
            }
            builder.redirectErrorStream(false);
            Process process = builder.start();

            String stdout;
            String stderr;
            try (BufferedReader outReader = new BufferedReader(
                    new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8));
                 BufferedReader errReader = new BufferedReader(
                         new InputStreamReader(process.getErrorStream(), StandardCharsets.UTF_8))) {
                stdout = outReader.lines().reduce("", (a, b) -> a.isEmpty() ? b : a + "\n" + b);
                stderr = errReader.lines().reduce("", (a, b) -> a.isEmpty() ? b : a + "\n" + b);
            }

            boolean finished = process.waitFor(timeoutMs, TimeUnit.MILLISECONDS);
            if (!finished) {
                process.destroyForcibly();
                throw new DriverException("Command timed out after " + timeoutMs + "ms");
            }
            return DataRecord.single(COMMAND_SCHEMA, Map.of(
                    "value", stdout.trim(),
                    "exitCode", process.exitValue(),
                    "stderr", stderr.trim()
            ));
        } catch (DriverException e) {
            throw e;
        } catch (Exception e) {
            throw new DriverException("Command failed: " + command, e);
        }
    }

    private static boolean isWindows() {
        return System.getProperty("os.name", "").toLowerCase().contains("win");
    }
}
