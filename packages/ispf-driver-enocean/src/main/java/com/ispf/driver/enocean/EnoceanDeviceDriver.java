package com.ispf.driver.enocean;

import com.ispf.core.model.DataRecord;
import com.ispf.core.model.DataSchema;
import com.ispf.core.model.FieldType;
import com.ispf.driver.DeviceDriver;
import com.ispf.driver.DriverException;
import com.ispf.driver.DriverMetadata;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * EnOcean ESP3 gateway driver — ASCII ESP3-lab telegrams over TCP.
 * <p>
 * Lab dialect (not USB CDC / radio PHY): newline commands
 * {@code GET &lt;idHex&gt;} and {@code TX &lt;idHex&gt; &lt;dataHex&gt;}.
 * Point mapping is a device id ({@code AABBCCDD}). Reads return last payload;
 * writes transmit {@code value} as data hex.
 * <p>
 * Clean-room ISPF code, Apache-2.0 — JDK sockets only. Not EnOcean Alliance SDK / TCM radio.
 */
public class EnoceanDeviceDriver implements DeviceDriver {

    private static final DataSchema VALUE_SCHEMA = DataSchema.builder("enoceanValue")
            .field("value", FieldType.STRING)
            .field("deviceId", FieldType.STRING)
            .field("raw", FieldType.STRING)
            .build();

    private static final DriverMetadata METADATA = new DriverMetadata(
            "enocean",
            "EnOcean Driver",
            "0.1.0",
            "EnOcean ESP3 TCP gateway ASCII lab: GET/TX (not radio PHY / Alliance SDK)",
            "ISPF",
            Map.of(
                    "host", "127.0.0.1",
                    "port", "54321",
                    "timeoutMs", "3000"
            ),
            null,
            Set.of("read", "write")
    );

    private DriverObject driverObject;
    private String host = "127.0.0.1";
    private int port = 54321;
    private int timeoutMs = 3000;
    private Socket socket;
    private final Map<String, String> deviceIds = new ConcurrentHashMap<>();
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
            case "host" -> host = value.trim();
            case "port" -> port = Integer.parseInt(value.trim());
            case "timeoutMs" -> timeoutMs = Integer.parseInt(value.trim());
            default -> { }
        }
    }

    @Override
    public void connect() throws DriverException {
        try {
            Socket next = new Socket();
            next.connect(new InetSocketAddress(host, port), timeoutMs);
            next.setSoTimeout(timeoutMs);
            next.setTcpNoDelay(true);
            socket = next;
            connected = true;
            driverObject.log(DriverLogLevel.INFO, "EnOcean ESP3-lab gateway connected to " + host + ":" + port);
        } catch (IOException e) {
            closeSocket();
            throw new DriverException("EnOcean connect failed for " + host + ":" + port, e);
        }
    }

    @Override
    public void disconnect() {
        connected = false;
        deviceIds.clear();
        closeSocket();
    }

    @Override
    public boolean isConnected() {
        return connected && socket != null && socket.isConnected() && !socket.isClosed();
    }

    @Override
    public void readPoints(Map<String, String> pointMappings) throws DriverException {
        ensureConnected();
        for (Map.Entry<String, String> entry : pointMappings.entrySet()) {
            String pointId = entry.getKey();
            String deviceId = normalizeId(entry.getValue() == null || entry.getValue().isBlank()
                    ? pointId : entry.getValue());
            deviceIds.put(pointId, deviceId);
            String raw = transact("GET " + deviceId);
            String data = extractData(raw);
            driverObject.updateVariable(pointId, DataRecord.single(VALUE_SCHEMA, Map.of(
                    "value", data,
                    "deviceId", deviceId,
                    "raw", raw
            )));
        }
    }

    @Override
    public void writePoint(String pointId, DataRecord value) throws DriverException {
        ensureConnected();
        String deviceId = deviceIds.getOrDefault(pointId, normalizeId(pointId));
        String data = extractValue(value).toUpperCase(Locale.ROOT).replace(" ", "");
        String raw = transact("TX " + deviceId + " " + data);
        driverObject.updateVariable(pointId, DataRecord.single(VALUE_SCHEMA, Map.of(
                "value", data,
                "deviceId", deviceId,
                "raw", raw
        )));
    }

    private synchronized String transact(String command) throws DriverException {
        try {
            writeLine(socket.getOutputStream(), command);
            String line = readLine(socket.getInputStream());
            if (line == null) {
                throw new IOException("EOF from EnOcean gateway");
            }
            return line.trim();
        } catch (IOException e) {
            throw new DriverException("EnOcean I/O failed for " + host + ":" + port, e);
        }
    }

    static String normalizeId(String mapping) {
        String t = mapping.trim().toUpperCase(Locale.ROOT);
        if (t.startsWith("0X")) {
            t = t.substring(2);
        }
        if (t.startsWith("GET ") || t.startsWith("TX ")) {
            t = t.substring(t.indexOf(' ') + 1).trim().split("\\s+")[0];
            if (t.startsWith("0X")) {
                t = t.substring(2);
            }
        }
        return t;
    }

    static String extractData(String raw) {
        if (raw == null || raw.isBlank()) {
            return "";
        }
        String[] parts = raw.trim().split("\\s+");
        if (parts.length >= 3 && (parts[0].equalsIgnoreCase("RX") || parts[0].equalsIgnoreCase("OK"))) {
            return parts[2].toUpperCase(Locale.ROOT);
        }
        if (parts.length >= 2 && parts[0].equalsIgnoreCase("DATA")) {
            return parts[1].toUpperCase(Locale.ROOT);
        }
        return raw.trim();
    }

    private static String extractValue(DataRecord value) {
        if (value == null || value.rowCount() == 0) {
            return "";
        }
        Object raw = value.firstRow().get("value");
        return raw == null ? "" : String.valueOf(raw).trim();
    }

    private void ensureConnected() throws DriverException {
        if (!isConnected()) {
            throw new DriverException("Not connected");
        }
    }

    private void closeSocket() {
        Socket current = socket;
        socket = null;
        if (current != null) {
            try {
                current.close();
            } catch (IOException ignored) {
                // best-effort
            }
        }
    }

    static void writeLine(OutputStream out, String line) throws IOException {
        out.write((line + "\r\n").getBytes(StandardCharsets.US_ASCII));
        out.flush();
    }

    static String readLine(InputStream in) throws IOException {
        ByteArrayOutputStream buf = new ByteArrayOutputStream();
        while (true) {
            int b = in.read();
            if (b < 0) {
                if (buf.size() == 0) {
                    return null;
                }
                break;
            }
            if (b == '\n') {
                break;
            }
            if (b != '\r') {
                buf.write(b);
            }
        }
        return buf.toString(StandardCharsets.US_ASCII);
    }
}
