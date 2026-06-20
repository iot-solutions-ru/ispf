package com.ispf.driver.telnet;

import com.ispf.core.model.DataRecord;
import com.ispf.core.model.DataSchema;
import com.ispf.core.model.FieldType;
import com.ispf.driver.DeviceDriver;
import com.ispf.driver.DriverException;
import com.ispf.driver.DriverMetadata;
import org.apache.commons.net.telnet.TelnetClient;

import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Telnet driver — executes remote commands via Apache Commons Net TelnetClient.
 */
public class TelnetDeviceDriver implements DeviceDriver {

    private static final DataSchema COMMAND_SCHEMA = DataSchema.builder("telnetCommand")
            .field("value", FieldType.STRING)
            .field("exitCode", FieldType.INTEGER)
            .field("stderr", FieldType.STRING)
            .build();

    private static final DriverMetadata METADATA = new DriverMetadata(
            "telnet",
            "Telnet Command Driver",
            "0.1.0",
            "Executes remote shell commands over Telnet and maps output to ISPF variables",
            "ISPF",
            Map.of(
                    "host", "127.0.0.1",
                    "port", "23",
                    "username", "",
                    "password", "",
                    "timeoutMs", "10000",
                    "pollIntervalMs", "60000"
            )
    );

    private DriverObject driverObject;
    private String host = "127.0.0.1";
    private int port = 23;
    private String username = "";
    private String password = "";
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
        if (value == null) {
            return;
        }
        switch (key) {
            case "host" -> host = value.trim();
            case "port" -> port = Integer.parseInt(value.trim());
            case "username" -> username = value.trim();
            case "password" -> password = value;
            case "timeoutMs" -> timeoutMs = Integer.parseInt(value.trim());
            default -> { }
        }
    }

    @Override
    public void connect() throws DriverException {
        connected = true;
        driverObject.log(DriverLogLevel.INFO, "Telnet driver ready for " + host + ":" + port);
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
                throw new DriverException("Telnet command mapping is blank for " + entry.getKey());
            }
            points.put(entry.getKey(), command);
            driverObject.updateVariable(entry.getKey(), execute(command));
        }
    }

    @Override
    public void writePoint(String pointId, DataRecord value) throws DriverException {
        throw new DriverException("Telnet driver is read-only in v0.1");
    }

    private DataRecord execute(String command) throws DriverException {
        TelnetClient client = new TelnetClient();
        try {
            client.setConnectTimeout(timeoutMs);
            client.connect(host, port);
            InputStream in = client.getInputStream();
            OutputStream out = client.getOutputStream();

            if (!username.isEmpty()) {
                readAvailable(in, 1000);
                writeLine(out, username);
                readAvailable(in, 1000);
            }
            if (!password.isEmpty()) {
                writeLine(out, password);
                readAvailable(in, 1000);
            }

            writeLine(out, command);
            String output = readAvailable(in, timeoutMs);

            return DataRecord.single(COMMAND_SCHEMA, Map.of(
                    "value", output.trim(),
                    "exitCode", 0,
                    "stderr", ""
            ));
        } catch (Exception e) {
            throw new DriverException("Telnet command failed: " + command, e);
        } finally {
            try {
                client.disconnect();
            } catch (Exception ignored) {
                // best effort
            }
        }
    }

    private static void writeLine(OutputStream out, String line) throws Exception {
        out.write((line + "\r\n").getBytes(StandardCharsets.UTF_8));
        out.flush();
    }

    private static String readAvailable(InputStream in, int waitMs) throws Exception {
        StringBuilder sb = new StringBuilder();
        long deadline = System.currentTimeMillis() + waitMs;
        byte[] buffer = new byte[1024];
        while (System.currentTimeMillis() < deadline) {
            while (in.available() > 0) {
                int read = in.read(buffer);
                if (read > 0) {
                    sb.append(new String(buffer, 0, read, StandardCharsets.UTF_8));
                }
            }
            Thread.sleep(50);
        }
        return sb.toString();
    }
}
