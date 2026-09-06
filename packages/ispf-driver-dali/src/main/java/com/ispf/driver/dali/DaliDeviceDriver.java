package com.ispf.driver.dali;

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
 * DALI lighting gateway driver — ASCII command lab over TCP.
 * <p>
 * Lab dialect (not IEC 62386 native Manchester PHY): client sends newline commands
 * {@code QUERY &lt;addr&gt;} / {@code SET &lt;addr&gt; &lt;level&gt;} where address is
 * {@code A0}..{@code A63}, {@code G0}..{@code G15}, or {@code BCAST}.
 * Point mapping is the DALI address ({@code A5}, {@code G1}, {@code BCAST}).
 * Reads return actual level; writes set arc power level 0..254 from record {@code value}.
 * <p>
 * Clean-room ISPF code, Apache-2.0 — JDK sockets only; not a DALI USB/dongle vendor SDK.
 */
public class DaliDeviceDriver implements DeviceDriver {

    private static final DataSchema VALUE_SCHEMA = DataSchema.builder("daliValue")
            .field("value", FieldType.STRING)
            .field("address", FieldType.STRING)
            .field("raw", FieldType.STRING)
            .build();

    private static final DriverMetadata METADATA = new DriverMetadata(
            "dali",
            "DALI Driver",
            "0.1.0",
            "DALI lighting gateway ASCII lab: QUERY/SET over TCP (not native IEC 62386 PHY)",
            "ISPF",
            Map.of(
                    "host", "127.0.0.1",
                    "port", "4001",
                    "timeoutMs", "3000"
            ),
            null,
            Set.of("read", "write")
    );

    private DriverObject driverObject;
    private String host = "127.0.0.1";
    private int port = 4001;
    private int timeoutMs = 3000;
    private Socket socket;
    private final Map<String, String> addresses = new ConcurrentHashMap<>();
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
            driverObject.log(DriverLogLevel.INFO, "DALI gateway connected to " + host + ":" + port);
        } catch (IOException e) {
            closeSocket();
            throw new DriverException("DALI connect failed for " + host + ":" + port, e);
        }
    }

    @Override
    public void disconnect() {
        connected = false;
        addresses.clear();
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
            String address = normalizeAddress(entry.getValue() == null || entry.getValue().isBlank()
                    ? pointId : entry.getValue());
            addresses.put(pointId, address);
            String raw = transact("QUERY " + address);
            String level = extractLevel(raw);
            driverObject.updateVariable(pointId, DataRecord.single(VALUE_SCHEMA, Map.of(
                    "value", level,
                    "address", address,
                    "raw", raw
            )));
        }
    }

    @Override
    public void writePoint(String pointId, DataRecord value) throws DriverException {
        ensureConnected();
        String address = addresses.getOrDefault(pointId, normalizeAddress(pointId));
        String level = extractValue(value);
        String raw = transact("SET " + address + " " + level);
        driverObject.updateVariable(pointId, DataRecord.single(VALUE_SCHEMA, Map.of(
                "value", extractLevel(raw).isBlank() ? level : extractLevel(raw),
                "address", address,
                "raw", raw
        )));
    }

    private synchronized String transact(String command) throws DriverException {
        try {
            writeLine(socket.getOutputStream(), command);
            String line = readLine(socket.getInputStream());
            if (line == null) {
                throw new IOException("EOF from DALI gateway");
            }
            return line.trim();
        } catch (IOException e) {
            throw new DriverException("DALI I/O failed for " + host + ":" + port, e);
        }
    }

    static String normalizeAddress(String mapping) {
        String t = mapping.trim().toUpperCase(Locale.ROOT);
        if (t.startsWith("QUERY ")) {
            t = t.substring(6).trim();
        } else if (t.startsWith("SET ")) {
            t = t.substring(4).trim().split("\\s+")[0];
        }
        return t;
    }

    static String extractLevel(String raw) {
        if (raw == null || raw.isBlank()) {
            return "";
        }
        String upper = raw.toUpperCase(Locale.ROOT);
        if (upper.startsWith("LEVEL ")) {
            return raw.substring(6).trim();
        }
        if (upper.startsWith("OK ")) {
            return raw.substring(3).trim();
        }
        return raw.trim();
    }

    private static String extractValue(DataRecord value) {
        if (value == null || value.rowCount() == 0) {
            return "0";
        }
        Object raw = value.firstRow().get("value");
        return raw == null ? "0" : String.valueOf(raw).trim();
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
