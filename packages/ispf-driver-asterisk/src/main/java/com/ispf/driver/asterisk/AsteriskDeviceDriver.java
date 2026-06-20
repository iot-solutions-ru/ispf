package com.ispf.driver.asterisk;

import com.ispf.core.model.DataRecord;
import com.ispf.core.model.DataSchema;
import com.ispf.core.model.FieldType;
import com.ispf.driver.DeviceDriver;
import com.ispf.driver.DriverException;
import com.ispf.driver.DriverMetadata;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Asterisk AMI driver — executes AMI commands over TCP.
 */
public class AsteriskDeviceDriver implements DeviceDriver {

    private static final DataSchema AMI_SCHEMA = DataSchema.builder("asteriskAmi")
            .field("value", FieldType.STRING)
            .field("response", FieldType.STRING)
            .field("success", FieldType.BOOLEAN)
            .build();

    private static final DriverMetadata METADATA = new DriverMetadata(
            "asterisk",
            "Asterisk AMI Driver",
            "0.1.0",
            "Executes Asterisk Manager Interface commands over TCP",
            "ISPF",
            Map.of(
                    "host", "127.0.0.1",
                    "port", "5038",
                    "username", "admin",
                    "secret", ""
            )
    );

    private DriverObject driverObject;
    private String host = "127.0.0.1";
    private int port = 5038;
    private String username = "admin";
    private String secret = "";
    private final Map<String, AsteriskPoint> points = new ConcurrentHashMap<>();
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
            case "secret" -> secret = value;
            default -> { }
        }
    }

    @Override
    public void connect() throws DriverException {
        connected = true;
        driverObject.log(DriverLogLevel.INFO, "Asterisk AMI ready for " + host + ":" + port);
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
            AsteriskPoint point = AsteriskPoint.parse(entry.getValue());
            points.put(entry.getKey(), point);
            driverObject.updateVariable(entry.getKey(), execute(point));
        }
    }

    @Override
    public void writePoint(String pointId, DataRecord value) throws DriverException {
        throw new DriverException("Asterisk AMI driver is read-only in v0.1");
    }

    private DataRecord execute(AsteriskPoint point) throws DriverException {
        try (Socket socket = new Socket()) {
            socket.connect(new InetSocketAddress(host, port), 5000);
            socket.setSoTimeout(5000);
            Writer out = new OutputStreamWriter(socket.getOutputStream(), StandardCharsets.UTF_8);
            BufferedReader in = new BufferedReader(
                    new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8));

            readBanner(in);
            login(out, in);
            out.write(point.toAmiMessage());
            out.flush();
            String response = readAmiResponse(in);
            boolean success = response.contains("Response: Success")
                    || response.contains("Ping: Pong");
            String value = extractResponseValue(response);
            return DataRecord.single(AMI_SCHEMA, Map.of(
                    "value", value,
                    "response", response.trim(),
                    "success", success
            ));
        } catch (IOException e) {
            throw new DriverException("Asterisk AMI command failed", e);
        }
    }

    private void readBanner(BufferedReader in) throws IOException {
        readUntilBlankLine(in);
    }

    private void login(Writer out, BufferedReader in) throws IOException {
        out.write("Action: Login\r\n");
        out.write("Username: " + username + "\r\n");
        out.write("Secret: " + secret + "\r\n");
        out.write("\r\n");
        out.flush();
        readUntilBlankLine(in);
    }

    private static String readAmiResponse(BufferedReader in) throws IOException {
        return readUntilBlankLine(in);
    }

    private static String readUntilBlankLine(BufferedReader in) throws IOException {
        StringBuilder sb = new StringBuilder();
        String line;
        while ((line = in.readLine()) != null) {
            sb.append(line).append('\n');
            if (line.isEmpty()) {
                break;
            }
        }
        return sb.toString();
    }

    private static String extractResponseValue(String response) {
        for (String line : response.split("\\R")) {
            if (line.startsWith("Ping:")) {
                return line.substring("Ping:".length()).trim();
            }
            if (line.startsWith("Message:")) {
                return line.substring("Message:".length()).trim();
            }
        }
        return response.trim();
    }
}
