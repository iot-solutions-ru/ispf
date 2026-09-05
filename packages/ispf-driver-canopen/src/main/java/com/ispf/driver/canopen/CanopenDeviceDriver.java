package com.ispf.driver.canopen;

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
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * CANopen driver — TCP gateway ASCII/binary lab (default port {@code 11898}).
 * <p>
 * Honesty boundary: this talks to an ISPF CANopen-over-TCP gateway lab, not SocketCAN,
 * not a CiA 301 stack on the wire, and not Vector/Peak/ETAS SDKs.
 * Lab line dialect:
 * <pre>
 *   SDO GET &lt;index&gt;:&lt;sub&gt;{@code \\n}  →  VALUE{@code \\n}
 *   SDO SET &lt;index&gt;:&lt;sub&gt; VALUE{@code \\n} →  OK{@code \\n}
 * </pre>
 * Point mappings accept {@code 0x2000:01}, {@code 2000:1}, or {@code index:sub}.
 * <p>
 * Clean-room ISPF code, Apache-2.0 — JDK sockets only.
 */
public class CanopenDeviceDriver implements DeviceDriver {

    private static final Pattern OD_MAPPING = Pattern.compile(
            "^(?:OD[:\\s-]*)?(?:0x)?([0-9A-Fa-f]+)\\s*[:.]\\s*(?:0x)?([0-9A-Fa-f]+)$",
            Pattern.CASE_INSENSITIVE);

    private static final DataSchema VALUE_SCHEMA = DataSchema.builder("canopenSdoValue")
            .field("value", FieldType.STRING)
            .field("index", FieldType.STRING)
            .field("sub", FieldType.STRING)
            .field("od", FieldType.STRING)
            .build();

    private static final DriverMetadata METADATA = new DriverMetadata(
            "canopen",
            "CANopen Driver",
            "0.1.0",
            "CANopen over TCP gateway lab (SDO GET/SET ASCII) — not SocketCAN / CiA stack",
            "ISPF",
            Map.of(
                    "host", "127.0.0.1",
                    "port", "11898",
                    "timeoutMs", "3000",
                    "pollIntervalMs", "5000"
            ),
            null,
            Set.of("read", "write")
    );

    private DriverObject driverObject;
    private String host = "127.0.0.1";
    private int port = 11898;
    private int timeoutMs = 3000;
    private Socket socket;
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
                    "CANopen TCP gateway lab connected to " + host + ":" + port
                            + " (not SocketCAN / CiA stack)");
        } catch (IOException e) {
            closeSocket();
            throw new DriverException("CANopen TCP gateway connect failed for " + host + ":" + port, e);
        }
    }

    @Override
    public void disconnect() {
        connected = false;
        points.clear();
        closeSocket();
    }

    @Override
    public boolean isConnected() {
        return connected && socket != null && socket.isConnected() && !socket.isClosed();
    }

    @Override
    public void readPoints(Map<String, String> pointMappings) throws DriverException {
        if (!isConnected()) {
            throw new DriverException("Not connected");
        }
        points.clear();
        for (Map.Entry<String, String> entry : pointMappings.entrySet()) {
            String pointId = entry.getKey();
            String mapping = entry.getValue() == null || entry.getValue().isBlank()
                    ? pointId
                    : entry.getValue().trim();
            points.put(pointId, mapping);
            OdAddress address = parseOdMapping(mapping);
            String value = sdoGet(address);
            driverObject.updateVariable(pointId, DataRecord.single(VALUE_SCHEMA, Map.of(
                    "value", value,
                    "index", formatIndex(address.index),
                    "sub", formatSub(address.sub),
                    "od", formatOd(address)
            )));
        }
    }

    @Override
    public void writePoint(String pointId, DataRecord value) throws DriverException {
        if (!isConnected()) {
            throw new DriverException("Not connected");
        }
        String mapping = points.getOrDefault(pointId, pointId);
        OdAddress address = parseOdMapping(mapping);
        String payload = extractValue(value);
        sdoSet(address, payload);
        driverObject.updateVariable(pointId, DataRecord.single(VALUE_SCHEMA, Map.of(
                "value", payload,
                "index", formatIndex(address.index),
                "sub", formatSub(address.sub),
                "od", formatOd(address)
        )));
    }

    private String sdoGet(OdAddress address) throws DriverException {
        String response = transact("SDO GET " + formatOd(address));
        String trimmed = response.trim();
        if (trimmed.regionMatches(true, 0, "ERR", 0, 3)) {
            throw new DriverException("CANopen SDO GET rejected: " + response);
        }
        if (trimmed.regionMatches(true, 0, "OK", 0, 2) && trimmed.length() > 2) {
            trimmed = trimmed.substring(2).trim();
        }
        if (trimmed.regionMatches(true, 0, "VALUE", 0, 5) && trimmed.length() > 5) {
            trimmed = trimmed.substring(5).trim();
        }
        return trimmed;
    }

    private void sdoSet(OdAddress address, String value) throws DriverException {
        String response = transact("SDO SET " + formatOd(address) + " " + value);
        if (!response.trim().toUpperCase(Locale.ROOT).startsWith("OK")) {
            throw new DriverException("CANopen SDO SET rejected: " + response);
        }
    }

    private synchronized String transact(String command) throws DriverException {
        try {
            writeLine(socket.getOutputStream(), command);
            return readLine(socket.getInputStream());
        } catch (IOException e) {
            throw new DriverException(
                    "CANopen TCP gateway I/O failed for " + host + ":" + port + " (" + command + ")", e);
        }
    }

    private void closeSocket() {
        Socket current = socket;
        socket = null;
        if (current != null) {
            try {
                current.close();
            } catch (IOException ignored) {
                // disconnect is best-effort
            }
        }
    }

    static OdAddress parseOdMapping(String mapping) {
        if (mapping == null || mapping.isBlank()) {
            throw new IllegalArgumentException("Blank CANopen OD mapping");
        }
        Matcher matcher = OD_MAPPING.matcher(mapping.trim());
        if (!matcher.matches()) {
            throw new IllegalArgumentException(
                    "Unsupported CANopen mapping (expected 0x2000:01 or index:sub): " + mapping);
        }
        // CANopen OD addresses are conventionally hexadecimal (even without a 0x prefix).
        int index = Integer.parseInt(matcher.group(1), 16);
        int sub = Integer.parseInt(matcher.group(2), 16);
        if (index < 0 || index > 0xFFFF) {
            throw new IllegalArgumentException("CANopen index out of range: " + index);
        }
        if (sub < 0 || sub > 0xFF) {
            throw new IllegalArgumentException("CANopen sub-index out of range: " + sub);
        }
        return new OdAddress(index, sub);
    }

    static String formatOd(OdAddress address) {
        return formatIndex(address.index) + ":" + formatSub(address.sub);
    }

    static String formatIndex(int index) {
        return String.format(Locale.ROOT, "0x%04X", index);
    }

    static String formatSub(int sub) {
        return String.format(Locale.ROOT, "%02X", sub);
    }

    static String extractValue(DataRecord value) {
        if (value == null || value.rowCount() == 0) {
            return "";
        }
        Map<String, Object> row = value.firstRow();
        for (String key : List.of("value", "data", "payload", "raw")) {
            Object candidate = row.get(key);
            if (candidate != null) {
                return String.valueOf(candidate).trim();
            }
        }
        if (row.size() == 1) {
            return String.valueOf(row.values().iterator().next()).trim();
        }
        throw new IllegalArgumentException("CANopen write requires a value field");
    }

    static void writeLine(OutputStream out, String line) throws IOException {
        out.write((line + "\n").getBytes(StandardCharsets.US_ASCII));
        out.flush();
    }

    static String readLine(InputStream in) throws IOException {
        ByteArrayOutputStream buf = new ByteArrayOutputStream();
        while (true) {
            int ch = in.read();
            if (ch < 0) {
                if (buf.size() == 0) {
                    throw new IOException("EOF reading CANopen gateway line");
                }
                break;
            }
            if (ch == '\n') {
                break;
            }
            if (ch != '\r') {
                buf.write(ch);
            }
        }
        return buf.toString(StandardCharsets.US_ASCII);
    }

    record OdAddress(int index, int sub) {
    }
}
