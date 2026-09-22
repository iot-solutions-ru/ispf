package com.ispf.driver.canopen;

import com.ispf.core.model.DataRecord;
import com.ispf.core.model.DataSchema;
import com.ispf.core.model.FieldType;
import com.ispf.driver.DeviceDriver;
import com.ispf.driver.DriverException;
import com.ispf.driver.DriverMaturity;
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
 * CANopen driver — LAWICEL SLCAN over TCP (default port {@code 11898}).
 * <p>
 * Standard frames use {@code t} + 3 hex id + 1 hex DLC + data hex + CR.
 * SDO upload request is id {@code 0x600+node}, DLC 8, command byte {@code 0x40},
 * index little-endian, subindex, four zero bytes. This is the USB-CAN adapter
 * protocol over TCP, not SocketCAN and not a full CiA 301 stack.
 * Point mappings accept {@code 0x2000:01}, {@code 2000:1}, or {@code index:sub}.
 * <p>
 * Clean-room ISPF code, Apache-2.0 — JDK sockets only.
 */
public class CanopenDeviceDriver implements DeviceDriver {

    private static final Pattern OD_MAPPING = Pattern.compile(
            "^(?:OD[:\\s-]*)?(?:0x)?([0-9A-Fa-f]+)\\s*[:.]\\s*(?:0x)?([0-9A-Fa-f]+)$",
            Pattern.CASE_INSENSITIVE);

    private static final Pattern SLCAN_STD = Pattern.compile(
            "^t([0-9A-Fa-f]{3})([0-9A-Fa-f])([0-9A-Fa-f]*)$");

    private static final DataSchema VALUE_SCHEMA = DataSchema.builder("canopenSdoValue")
            .field("value", FieldType.STRING)
            .field("index", FieldType.STRING)
            .field("sub", FieldType.STRING)
            .field("od", FieldType.STRING)
            .build();

    private static final DriverMetadata METADATA = new DriverMetadata(
            "canopen",
            "CANopen Driver",
            "1.0.0",
            "CANopen SDO via LAWICEL SLCAN over TCP (standard t frames);"
                    + " not SocketCAN / CiA stack / Vector-Peak SDK",
            "ISPF",
            Map.of(
                    "host", "127.0.0.1",
                    "port", "11898",
                    "nodeId", "1",
                    "timeoutMs", "3000",
                    "pollIntervalMs", "5000"
            ),
            DriverMaturity.BETA,
            Set.of("read", "write")
    );

    private DriverObject driverObject;
    private String host = "127.0.0.1";
    private int port = 11898;
    private int nodeId = 1;
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
            case "nodeId", "node" -> nodeId = Integer.parseInt(value.trim());
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
                    "CANopen SLCAN connected to " + host + ":" + port
                            + " (not SocketCAN / CiA stack)");
        } catch (IOException e) {
            closeSocket();
            throw new DriverException("CANopen SLCAN connect failed for " + host + ":" + port, e);
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
            String value = sdoUpload(address);
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
        sdoDownload(address, payload);
        driverObject.updateVariable(pointId, DataRecord.single(VALUE_SCHEMA, Map.of(
                "value", payload,
                "index", formatIndex(address.index),
                "sub", formatSub(address.sub),
                "od", formatOd(address)
        )));
    }

    private String sdoUpload(OdAddress address) throws DriverException {
        String request = formatSdoUploadRequest(nodeId, address.index, address.sub);
        String response = transact(request);
        return parseSdoUploadResponse(response, address);
    }

    private void sdoDownload(OdAddress address, String value) throws DriverException {
        long numeric = parseNumericValue(value);
        String request = formatSdoDownloadRequest(nodeId, address.index, address.sub, numeric);
        String response = transact(request);
        String trimmed = response.trim();
        if (!(trimmed.equalsIgnoreCase("z") || tryParseSlcanStandard(trimmed) != null)) {
            throw new DriverException("CANopen SDO download rejected: " + response);
        }
    }

    private synchronized String transact(String command) throws DriverException {
        try {
            writeAscii(socket.getOutputStream(), command);
            return readUntilCr(socket.getInputStream());
        } catch (IOException e) {
            throw new DriverException(
                    "CANopen SLCAN I/O failed for " + host + ":" + port, e);
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

    /**
     * SDO upload request for node 1, index 0x2000, sub 0:
     * {@code t6018400020000000000000\r}
     */
    static String formatSdoUploadRequest(int nodeId, int index, int sub) {
        int canId = 0x600 + (nodeId & 0x7F);
        String data = String.format(Locale.ROOT, "40%02X%02X%02X00000000",
                index & 0xFF, (index >> 8) & 0xFF, sub & 0xFF);
        return formatStandardFrame(canId, data);
    }

    static String formatSdoDownloadRequest(int nodeId, int index, int sub, long value) {
        int canId = 0x600 + (nodeId & 0x7F);
        // Expedited 4-byte download: command 0x23
        String data = String.format(Locale.ROOT, "23%02X%02X%02X%02X%02X%02X%02X",
                index & 0xFF, (index >> 8) & 0xFF, sub & 0xFF,
                (int) (value & 0xFF),
                (int) ((value >> 8) & 0xFF),
                (int) ((value >> 16) & 0xFF),
                (int) ((value >> 24) & 0xFF));
        return formatStandardFrame(canId, data);
    }

    static String formatStandardFrame(int canId, String dataHex) {
        String data = normalizeHex(dataHex);
        int dlc = data.length() / 2;
        if (dlc > 8) {
            throw new IllegalArgumentException("CANopen DLC exceeds 8: " + dlc);
        }
        return "t"
                + String.format(Locale.ROOT, "%03X", canId & 0x7FF)
                + Integer.toHexString(dlc).toUpperCase(Locale.ROOT)
                + data
                + "\r";
    }

    static String parseSdoUploadResponse(String line, OdAddress expected) {
        SlcanFrame frame = tryParseSlcanStandard(line);
        if (frame == null) {
            throw new IllegalArgumentException("Invalid SLCAN SDO response: " + line);
        }
        String data = frame.dataHex();
        if (data.length() < 8) {
            throw new IllegalArgumentException("SDO response too short: " + line);
        }
        int cmd = Integer.parseInt(data.substring(0, 2), 16);
        int index = Integer.parseInt(data.substring(2, 4), 16)
                | (Integer.parseInt(data.substring(4, 6), 16) << 8);
        int sub = Integer.parseInt(data.substring(6, 8), 16);
        if (index != expected.index || sub != expected.sub) {
            throw new IllegalArgumentException("SDO response OD mismatch: " + line);
        }
        int n = switch (cmd) {
            case 0x4F -> 1;
            case 0x4B -> 2;
            case 0x47 -> 3;
            case 0x43 -> 4;
            default -> 4;
        };
        String payload = data.substring(8);
        if (payload.length() < n * 2) {
            return Long.toString(Long.parseLong(payload.isEmpty() ? "0" : payload, 16));
        }
        // little-endian numeric
        long value = 0;
        for (int i = 0; i < n; i++) {
            int b = Integer.parseInt(payload.substring(i * 2, i * 2 + 2), 16);
            value |= ((long) b) << (8 * i);
        }
        return Long.toString(value);
    }

    static SlcanFrame tryParseSlcanStandard(String line) {
        if (line == null) {
            return null;
        }
        String trimmed = line.trim();
        if (trimmed.endsWith("\r")) {
            trimmed = trimmed.substring(0, trimmed.length() - 1);
        }
        Matcher matcher = SLCAN_STD.matcher(trimmed);
        if (!matcher.matches()) {
            return null;
        }
        int canId = Integer.parseInt(matcher.group(1), 16);
        int dlc = Integer.parseInt(matcher.group(2), 16);
        String data = normalizeHex(matcher.group(3));
        if (data.length() != dlc * 2) {
            return null;
        }
        return new SlcanFrame(canId, data);
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

    static long parseNumericValue(String text) {
        String raw = text == null ? "0" : text.trim();
        if (raw.regionMatches(true, 0, "0x", 0, 2)) {
            return Long.parseLong(raw.substring(2), 16);
        }
        return Long.parseLong(raw);
    }

    static String normalizeHex(String raw) {
        if (raw == null || raw.isBlank()) {
            return "";
        }
        String hex = raw.trim().replace(" ", "").toUpperCase(Locale.ROOT);
        if (!hex.matches("[0-9A-F]*") || (hex.length() % 2) != 0) {
            throw new IllegalArgumentException("Hex must be even-length: " + raw);
        }
        return hex;
    }

    static void writeAscii(OutputStream out, String line) throws IOException {
        out.write(line.getBytes(StandardCharsets.US_ASCII));
        out.flush();
    }

    static String readUntilCr(InputStream in) throws IOException {
        ByteArrayOutputStream buf = new ByteArrayOutputStream();
        while (true) {
            int ch = in.read();
            if (ch < 0) {
                if (buf.size() == 0) {
                    throw new IOException("EOF reading SLCAN line");
                }
                break;
            }
            if (ch == '\r') {
                break;
            }
            if (ch != '\n') {
                buf.write(ch);
            }
        }
        return buf.toString(StandardCharsets.US_ASCII);
    }

    record OdAddress(int index, int sub) {
    }

    record SlcanFrame(int canId, String dataHex) {
    }
}
