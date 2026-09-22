package com.ispf.driver.enocean;

import com.ispf.core.model.DataRecord;
import com.ispf.core.model.DataSchema;
import com.ispf.core.model.FieldType;
import com.ispf.driver.DeviceDriver;
import com.ispf.driver.DriverException;
import com.ispf.driver.DriverMetadata;
import com.ispf.driver.enocean.codec.Esp3Codec;
import com.ispf.driver.enocean.codec.Esp3Packet;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * EnOcean ESP3 gateway driver — ESP3 framing over TCP (default port {@code 54321}).
 * <p>
 * Point mapping is a 4-byte device id ({@code AABBCCDD}). Reads poll with an ESP3 RADIO
 * telegram (device id, empty payload); writes send RADIO with device id + data hex.
 * Locked COMMON_COMMAND {@code CO_RD_VERSION} request literal starts with sync {@code 0x55}.
 * <p>
 * Clean-room ISPF code, Apache-2.0 — JDK sockets only. Not EnOcean Alliance SDK / TCM radio PHY.
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
            "1.0.0",
            "EnOcean ESP3 serial-over-TCP (sync 0x55, CRC8 poly 0x07);"
                    + " not Alliance SDK / TCM radio PHY",
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
    private final Map<String, byte[]> deviceIds = new ConcurrentHashMap<>();
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
                    "EnOcean ESP3 connected to " + host + ":" + port
                            + " (not Alliance SDK / TCM radio PHY)");
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
            byte[] deviceId = parseDeviceId(entry.getValue() == null || entry.getValue().isBlank()
                    ? pointId : entry.getValue());
            deviceIds.put(pointId, deviceId);
            byte[] request = Esp3Codec.encodeRadio(deviceId, new byte[0]);
            Esp3Packet reply = exchange(request);
            if (reply.packetType() != Esp3Codec.TYPE_RADIO) {
                throw new DriverException("Unexpected ESP3 type 0x"
                        + Integer.toHexString(reply.packetType()));
            }
            String data = toHex(reply.payload());
            driverObject.updateVariable(pointId, DataRecord.single(VALUE_SCHEMA, Map.of(
                    "value", data,
                    "deviceId", toHex(deviceId),
                    "raw", toHex(request) + "/" + toHex(Esp3Codec.encode(
                            reply.packetType(), reply.data(), reply.optional()))
            )));
        }
    }

    @Override
    public void writePoint(String pointId, DataRecord value) throws DriverException {
        ensureConnected();
        byte[] deviceId = deviceIds.get(pointId);
        if (deviceId == null) {
            deviceId = parseDeviceId(pointId);
        }
        byte[] payload = parseHex(extractValue(value));
        byte[] request = Esp3Codec.encodeRadio(deviceId, payload);
        Esp3Packet reply = exchange(request);
        if (reply.packetType() != Esp3Codec.TYPE_RESPONSE
                || reply.data().length < 1
                || (reply.data()[0] & 0xFF) != Esp3Codec.RET_OK) {
            throw new DriverException("EnOcean ESP3 write rejected");
        }
        driverObject.updateVariable(pointId, DataRecord.single(VALUE_SCHEMA, Map.of(
                "value", toHex(payload),
                "deviceId", toHex(deviceId),
                "raw", toHex(request)
        )));
    }

    private synchronized Esp3Packet exchange(byte[] request) throws DriverException {
        try {
            Esp3Codec.writePacket(socket.getOutputStream(), request);
            return Esp3Codec.readPacket(socket.getInputStream());
        } catch (IOException e) {
            throw new DriverException("EnOcean I/O failed for " + host + ":" + port, e);
        }
    }

    /** Handwritten {@code CO_RD_VERSION} ESP3 request (first byte {@code 0x55}). */
    public static byte[] coRdVersionRequestLiteral() {
        return Esp3Codec.coRdVersionRequestLiteral();
    }

    static byte[] parseDeviceId(String mapping) {
        String hex = normalizeHex(mapping);
        if (hex.length() != 8) {
            throw new IllegalArgumentException(
                    "EnOcean device id must be 4 bytes hex (e.g. AABBCCDD): " + mapping);
        }
        return parseHex(hex);
    }

    static byte[] parseHex(String raw) {
        String hex = normalizeHex(raw);
        if ((hex.length() % 2) != 0) {
            throw new IllegalArgumentException("Hex must be even-length: " + raw);
        }
        byte[] out = new byte[hex.length() / 2];
        for (int i = 0; i < out.length; i++) {
            out[i] = (byte) Integer.parseInt(hex.substring(i * 2, i * 2 + 2), 16);
        }
        return out;
    }

    static String normalizeHex(String raw) {
        if (raw == null) {
            return "";
        }
        String t = raw.trim().toUpperCase(Locale.ROOT).replace(" ", "");
        if (t.startsWith("0X")) {
            t = t.substring(2);
        }
        if (!t.matches("[0-9A-F]*")) {
            throw new IllegalArgumentException("Invalid hex: " + raw);
        }
        return t;
    }

    static String toHex(byte[] data) {
        if (data == null || data.length == 0) {
            return "";
        }
        StringBuilder sb = new StringBuilder(data.length * 2);
        for (byte value : data) {
            sb.append(String.format(Locale.ROOT, "%02X", value & 0xFF));
        }
        return sb.toString();
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
}
