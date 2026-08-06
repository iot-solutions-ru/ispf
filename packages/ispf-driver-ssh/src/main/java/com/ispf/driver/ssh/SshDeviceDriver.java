package com.ispf.driver.ssh;

import com.ispf.core.model.DataRecord;
import com.ispf.core.model.DataSchema;
import com.ispf.core.model.FieldType;
import com.ispf.driver.DeviceDriver;
import com.ispf.driver.DriverException;
import com.ispf.driver.DriverMaturity;
import com.ispf.driver.DriverMetadata;
import com.jcraft.jsch.ChannelExec;
import com.jcraft.jsch.JSch;
import com.jcraft.jsch.Session;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;

/**
 * SSH driver — poll remote shell commands; optional opt-in {@code writePoint} for one-shot exec.
 * Point mapping: shell command string per variable name.
 */
public class SshDeviceDriver implements DeviceDriver {

    private static final DataSchema COMMAND_SCHEMA = DataSchema.builder("sshCommand")
            .field("value", FieldType.STRING)
            .field("exitCode", FieldType.INTEGER)
            .field("stderr", FieldType.STRING)
            .build();

    private static final DriverMetadata METADATA = new DriverMetadata(
            "ssh",
            "SSH Command Driver",
            "0.2.0",
            "Executes remote shell commands over SSH (poll + opt-in write). writeEnabled + allowlist required for write.",
            "ISPF",
            Map.of(
                    "host", "127.0.0.1",
                    "port", "22",
                    "username", "admin",
                    "password", "",
                    "timeoutMs", "10000",
                    "pollIntervalMs", "60000",
                    "writeEnabled", "false",
                    "writeCommandAllowlist", ""
            ),
            DriverMaturity.PRODUCTION,
            Set.of("read", "write")
    );

    private DriverObject driverObject;
    private String host = "127.0.0.1";
    private int port = 22;
    private String username = "admin";
    private String password = "";
    private int timeoutMs = 10_000;
    private boolean writeEnabled;
    private final List<Pattern> writeAllowlist = new ArrayList<>();
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
        switch (key) {
            case "host" -> host = value.trim();
            case "port" -> port = Integer.parseInt(value.trim());
            case "username" -> username = value.trim();
            case "password" -> password = value;
            case "timeoutMs" -> timeoutMs = Integer.parseInt(value.trim());
            case "writeEnabled" -> writeEnabled = Boolean.parseBoolean(value.trim());
            case "writeCommandAllowlist" -> parseAllowlist(value);
            default -> { }
        }
    }

    private void parseAllowlist(String raw) {
        writeAllowlist.clear();
        if (raw.isBlank()) {
            return;
        }
        for (String part : raw.split("[,\\n]")) {
            String token = part.trim();
            if (!token.isEmpty()) {
                writeAllowlist.add(Pattern.compile(token));
            }
        }
    }

    @Override
    public void connect() throws DriverException {
        connected = true;
        driverObject.log(DriverLogLevel.INFO, "SSH driver ready for " + username + "@" + host + ":" + port);
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
                throw new DriverException("SSH command mapping is blank for " + entry.getKey());
            }
            points.put(entry.getKey(), command);
            driverObject.updateVariable(entry.getKey(), execute(command));
        }
    }

    @Override
    public void writePoint(String pointId, DataRecord value) throws DriverException {
        if (!isConnected()) {
            throw new DriverException("Not connected");
        }
        if (!writeEnabled) {
            throw new DriverException("SSH write disabled (set writeEnabled=true and writeCommandAllowlist)");
        }
        if (writeAllowlist.isEmpty()) {
            throw new DriverException("SSH write requires non-empty writeCommandAllowlist");
        }
        if (value == null || value.rowCount() == 0) {
            throw new DriverException("SSH write requires a non-empty DataRecord");
        }
        String mapped = points.get(pointId);
        Map<String, Object> row = value.firstRow();
        String command = firstNonBlank(row, "command", "value", "raw");
        if (command == null || command.isBlank()) {
            command = mapped;
        }
        if (command == null || command.isBlank()) {
            throw new DriverException("SSH write requires command (field or mapped point " + pointId + ")");
        }
        command = command.trim();
        if (!isAllowed(command)) {
            throw new DriverException("SSH write command not allowlisted: " + command);
        }
        driverObject.log(DriverLogLevel.INFO, "SSH write exec: " + command);
        DataRecord result = execute(command);
        driverObject.updateVariable(pointId, result);
    }

    private boolean isAllowed(String command) {
        for (Pattern pattern : writeAllowlist) {
            if (pattern.matcher(command).matches()) {
                return true;
            }
        }
        return false;
    }

    private static String firstNonBlank(Map<String, Object> row, String... keys) {
        for (String key : keys) {
            Object value = row.get(key);
            if (value != null && !String.valueOf(value).isBlank()) {
                return String.valueOf(value);
            }
        }
        return null;
    }

    private DataRecord execute(String command) throws DriverException {
        Session session = null;
        ChannelExec channel = null;
        try {
            JSch jsch = new JSch();
            session = jsch.getSession(username, host, port);
            session.setPassword(password);
            Properties config = new Properties();
            config.put("StrictHostKeyChecking", "no");
            session.setConfig(config);
            session.connect(timeoutMs);

            channel = (ChannelExec) session.openChannel("exec");
            channel.setCommand(command);
            ByteArrayOutputStream stdout = new ByteArrayOutputStream();
            ByteArrayOutputStream stderr = new ByteArrayOutputStream();
            channel.setOutputStream(stdout);
            channel.setErrStream(stderr);
            channel.connect(timeoutMs);

            while (!channel.isClosed()) {
                Thread.sleep(50);
            }

            int exitCode = channel.getExitStatus();
            return DataRecord.single(COMMAND_SCHEMA, Map.of(
                    "value", stdout.toString(StandardCharsets.UTF_8).trim(),
                    "exitCode", exitCode,
                    "stderr", stderr.toString(StandardCharsets.UTF_8).trim()
            ));
        } catch (Exception e) {
            throw new DriverException("SSH command failed: " + command, e);
        } finally {
            if (channel != null) {
                channel.disconnect();
            }
            if (session != null) {
                session.disconnect();
            }
        }
    }
}
