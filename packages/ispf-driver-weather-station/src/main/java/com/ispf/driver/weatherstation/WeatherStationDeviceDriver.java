package com.ispf.driver.weatherstation;

import com.ispf.core.model.DataRecord;
import com.ispf.core.model.DataSchema;
import com.ispf.core.model.FieldType;
import com.ispf.driver.DeviceDriver;
import com.ispf.driver.DriverException;
import com.ispf.driver.DriverMetadata;
import com.ispf.driver.weatherstation.codec.WeatherStationCodec;

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
 * Weather station driver — Davis Vantage {@code LOOP} command over TCP.
 * <p>
 * Sends {@code LOOP\n} ({@code 4C 4F 4F 50 0A}), expects ACK {@code 0x06} then a truncated
 * LOOP payload (not the full 99-byte Vantage packet). Not a Vaisala sensor protocol.
 * Point mapping is a label (for example {@code loop} or {@code TEMP}); reads are poll-only.
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
            "Davis Vantage LOOP command over TCP (LOOP\\n + ACK); not the full 99-byte Vantage packet;"
                    + " not a Vaisala sensor",
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
            driverObject.log(DriverLogLevel.INFO,
                    "Weather station LOOP TCP connected to " + host + ":" + port);
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
            String raw = queryLoop();
            driverObject.updateVariable(pointId, DataRecord.single(VALUE_SCHEMA, Map.of(
                    "value", raw,
                    "field", field.toUpperCase(Locale.ROOT),
                    "raw", raw
            )));
        }
    }

    @Override
    public void writePoint(String pointId, DataRecord value) throws DriverException {
        throw new DriverException("weather-station is read-only (Davis Vantage LOOP over TCP)");
    }

    static byte[] buildLoopCommand() {
        return WeatherStationCodec.encodeLoopCommand();
    }

    private synchronized String queryLoop() throws DriverException {
        try {
            OutputStream out = socket.getOutputStream();
            InputStream in = socket.getInputStream();
            byte[] command = WeatherStationCodec.encodeLoopCommand();
            out.write(command);
            out.flush();
            int ack = in.read();
            if (ack < 0) {
                throw new IOException("EOF waiting for LOOP ACK");
            }
            if (ack != WeatherStationCodec.ACK) {
                throw new IOException("Expected LOOP ACK 0x06, got 0x"
                        + Integer.toHexString(ack & 0xFF));
            }
            byte[] payload = readAvailablePayload(in);
            return new String(payload, StandardCharsets.US_ASCII);
        } catch (IOException e) {
            throw new DriverException("Weather station LOOP failed for " + host + ":" + port, e);
        }
    }

    /**
     * Reads a short truncated LOOP payload after ACK (not the full 99-byte Vantage packet).
     */
    private static byte[] readAvailablePayload(InputStream in) throws IOException {
        ByteArrayOutputStream buf = new ByteArrayOutputStream();
        byte[] chunk = new byte[64];
        // At least try one blocking read for the truncated ack body
        int n = in.read(chunk);
        if (n > 0) {
            buf.write(chunk, 0, n);
        }
        while (in.available() > 0) {
            n = in.read(chunk);
            if (n <= 0) {
                break;
            }
            buf.write(chunk, 0, n);
        }
        return buf.toByteArray();
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
}
