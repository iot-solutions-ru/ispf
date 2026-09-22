package com.ispf.driver.dali;

import com.ispf.core.model.DataRecord;
import com.ispf.core.model.DataSchema;
import com.ispf.core.model.FieldType;
import com.ispf.driver.DeviceDriver;
import com.ispf.driver.DriverException;
import com.ispf.driver.DriverMetadata;
import com.ispf.driver.dali.codec.DaliCodec;

import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * DALI driver — IEC 62386 forward frames (two bytes) over TCP (default port {@code 4001}).
 * <p>
 * Point mapping is the DALI address ({@code A5}, {@code G1}, {@code BCAST}). Reads issue
 * QUERY ACTUAL LEVEL; writes send DAPC (arc power level 0..254) from record {@code value}.
 * <p>
 * Clean-room ISPF code, Apache-2.0 — JDK sockets only; not a DALI USB/dongle vendor SDK.
 */
public class DaliDeviceDriver implements DeviceDriver {

    private static final Pattern SHORT = Pattern.compile("^A(\\d{1,2})$", Pattern.CASE_INSENSITIVE);
    private static final Pattern GROUP = Pattern.compile("^G(\\d{1,2})$", Pattern.CASE_INSENSITIVE);

    private static final DataSchema VALUE_SCHEMA = DataSchema.builder("daliValue")
            .field("value", FieldType.STRING)
            .field("address", FieldType.STRING)
            .field("raw", FieldType.STRING)
            .build();

    private static final DriverMetadata METADATA = new DriverMetadata(
            "dali",
            "DALI Driver",
            "1.0.0",
            "IEC 62386 DALI forward frames (two bytes) over TCP;"
                    + " not native Manchester PHY / vendor USB dongle SDK",
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
            driverObject.log(DriverLogLevel.INFO,
                    "DALI IEC 62386 TCP connected to " + host + ":" + port);
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
            byte[] frame = buildQueryFrame(address);
            int level = transact(frame);
            driverObject.updateVariable(pointId, DataRecord.single(VALUE_SCHEMA, Map.of(
                    "value", Integer.toString(level),
                    "address", address,
                    "raw", hex(frame) + " -> " + level
            )));
        }
    }

    @Override
    public void writePoint(String pointId, DataRecord value) throws DriverException {
        ensureConnected();
        String address = addresses.getOrDefault(pointId, normalizeAddress(pointId));
        int level = parseLevel(extractValue(value));
        byte[] frame = buildDapcFrame(address, level);
        int echoed = transact(frame);
        driverObject.updateVariable(pointId, DataRecord.single(VALUE_SCHEMA, Map.of(
                "value", Integer.toString(echoed >= 0 ? echoed : level),
                "address", address,
                "raw", hex(frame)
        )));
    }

    private synchronized int transact(byte[] frame) throws DriverException {
        try {
            OutputStream out = socket.getOutputStream();
            InputStream in = socket.getInputStream();
            out.write(frame);
            out.flush();
            int b = in.read();
            if (b < 0) {
                throw new EOFException("EOF from DALI peer");
            }
            return b & 0xFF;
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

    static byte[] buildQueryFrame(String address) {
        return DaliCodec.buildCommand(addressByte(address, true), DaliCodec.CMD_QUERY_ACTUAL_LEVEL);
    }

    static byte[] buildDapcFrame(String address, int level) {
        return DaliCodec.buildDapc(addressByte(address, false), level);
    }

    static int addressByte(String address, boolean command) {
        String upper = address.toUpperCase(Locale.ROOT);
        if ("BCAST".equals(upper) || "BROADCAST".equals(upper)) {
            return command ? DaliCodec.broadcastCommandByte() : DaliCodec.broadcastDapcByte();
        }
        Matcher shortAddr = SHORT.matcher(upper);
        if (shortAddr.matches()) {
            int s = Integer.parseInt(shortAddr.group(1));
            if (s < 0 || s > 63) {
                throw new IllegalArgumentException("DALI short address out of range: " + s);
            }
            return command ? DaliCodec.shortAddressCommandByte(s) : DaliCodec.shortAddressDapcByte(s);
        }
        Matcher group = GROUP.matcher(upper);
        if (group.matches()) {
            int g = Integer.parseInt(group.group(1));
            if (g < 0 || g > 15) {
                throw new IllegalArgumentException("DALI group out of range: " + g);
            }
            return command ? DaliCodec.groupCommandByte(g) : DaliCodec.groupDapcByte(g);
        }
        throw new IllegalArgumentException("Unsupported DALI address: " + address);
    }

    static int parseLevel(String raw) {
        String text = raw == null ? "0" : raw.trim();
        if (text.isEmpty()) {
            return 0;
        }
        int level = Integer.parseInt(text);
        if (level < 0 || level > 254) {
            throw new IllegalArgumentException("DALI level out of range: " + level);
        }
        return level;
    }

    private static String extractValue(DataRecord value) {
        if (value == null || value.rowCount() == 0) {
            return "0";
        }
        Object raw = value.firstRow().get("value");
        return raw == null ? "0" : String.valueOf(raw).trim();
    }

    private static String hex(byte[] frame) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < frame.length; i++) {
            if (i > 0) {
                sb.append(' ');
            }
            sb.append(String.format(Locale.ROOT, "%02X", frame[i] & 0xFF));
        }
        return sb.toString();
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

    public static byte[] buildOffShortAddress0Frame() {
        return DaliCodec.buildOffShortAddress0();
    }
}
