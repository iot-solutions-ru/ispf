package com.ispf.driver.weatherstation;

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
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Weather station driver — Davis/Vaisala-class lab text protocol over TCP.
 * <p>
 * Lab dialect (not a vendor binary LOOP frame): client sends {@code GET &lt;FIELD&gt;} or
 * {@code GET ALL}; station replies with {@code KEY=value} pairs on one line
 * (for example {@code TEMP=21.5 HUM=55 PRESS=1013.2 WIND=3.2}).
 * <p>
 * Point mapping is the field name ({@code TEMP}, {@code HUM}, {@code PRESS}, {@code WIND})
 * or {@code ALL} for the raw line. Reads are poll-only; writes are not supported.
 * <p>
 * Clean-room ISPF code, Apache-2.0 — JDK sockets only.
 */
public class WeatherStationDeviceDriver implements DeviceDriver {

    private static final DataSchema VALUE_SCHEMA = DataSchema.builder("weatherValue")
            .field("value", FieldType.STRING)
            .field("field", FieldType.STRING)
            .field("raw", FieldType.STRING)
            .build();

    private static final DriverMetadata METADATA = new DriverMetadata(
            "weather-station",
            "Weather station Driver",
            "0.1.0",
            "Davis/Vaisala-class lab text weather station: GET FIELD / GET ALL (read-only)",
            "ISPF",
            Map.of(
                    "host", "127.0.0.1",
                    "port", "22222",
                    "timeoutMs", "3000"
            ),
            null,
            Set.of("read")
    );

    private DriverObject driverObject;
    private String host = "127.0.0.1";
    private int port = 22222;
    private int timeoutMs = 3000;
    private Socket socket;
    private final Map<String, String> fields = new ConcurrentHashMap<>();
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
            driverObject.log(DriverLogLevel.INFO, "Weather station connected to " + host + ":" + port);
        } catch (IOException e) {
            closeSocket();
            throw new DriverException("Weather station connect failed for " + host + ":" + port, e);
        }
    }

    @Override
    public void disconnect() {
        connected = false;
        fields.clear();
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
            String field = entry.getValue() == null || entry.getValue().isBlank()
                    ? pointId
                    : entry.getValue().trim();
            fields.put(pointId, field);
            String requestField = field.toUpperCase(Locale.ROOT);
            String raw = query(requestField);
            String value = "ALL".equals(requestField) || "*".equals(requestField)
                    ? raw
                    : extractField(raw, requestField);
            driverObject.updateVariable(pointId, DataRecord.single(VALUE_SCHEMA, Map.of(
                    "value", value == null ? "" : value,
                    "field", requestField,
                    "raw", raw
            )));
        }
    }

    @Override
    public void writePoint(String pointId, DataRecord value) throws DriverException {
        throw new DriverException("weather-station is read-only (GET poll lab dialect)");
    }

    private synchronized String query(String field) throws DriverException {
        try {
            String command = "ALL".equals(field) || "*".equals(field) ? "GET ALL" : "GET " + field;
            writeLine(socket.getOutputStream(), command);
            String line = readLine(socket.getInputStream());
            if (line == null) {
                throw new IOException("EOF from weather station");
            }
            return line.trim();
        } catch (IOException e) {
            throw new DriverException("Weather station query failed for " + host + ":" + port, e);
        }
    }

    static String extractField(String raw, String field) {
        Map<String, String> pairs = parsePairs(raw);
        String direct = pairs.get(field.toUpperCase(Locale.ROOT));
        return direct == null ? "" : direct;
    }

    static Map<String, String> parsePairs(String raw) {
        Map<String, String> out = new LinkedHashMap<>();
        if (raw == null || raw.isBlank()) {
            return out;
        }
        for (String token : raw.trim().split("\\s+")) {
            int eq = token.indexOf('=');
            if (eq > 0) {
                out.put(token.substring(0, eq).toUpperCase(Locale.ROOT), token.substring(eq + 1));
            }
        }
        return out;
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
