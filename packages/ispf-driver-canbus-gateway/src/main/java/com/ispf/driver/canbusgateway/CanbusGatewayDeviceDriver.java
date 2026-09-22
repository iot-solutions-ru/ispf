package com.ispf.driver.canbusgateway;

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
 * CAN bus TCP gateway — LAWICEL SLCAN over TCP (default port {@code 29536}).
 * <p>
 * Standard frames use {@code t} + 3 hex id + 1 hex DLC + data hex + CR.
 * Example: id {@code 0x001}, DLC {@code 2}, data {@code 11 22} encodes as
 * {@code t00121122\r}. Point mapping is a CAN id ({@code 18FF50E5}, {@code 0x123}, {@code 123}).
 * <p>
 * Clean-room ISPF code, Apache-2.0 — JDK sockets only. Not SocketCAN / Peak/Vector SDKs.
 */
public class CanbusGatewayDeviceDriver implements DeviceDriver {

    private static final Pattern SLCAN_STD = Pattern.compile(
            "^t([0-9A-Fa-f]{3})([0-9A-Fa-f])([0-9A-Fa-f]*)$");

    private static final DataSchema VALUE_SCHEMA = DataSchema.builder("canGatewayValue")
            .field("value", FieldType.STRING)
            .field("canId", FieldType.STRING)
            .field("raw", FieldType.STRING)
            .build();

    private static final DriverMetadata METADATA = new DriverMetadata(
            "canbus-gateway",
            "CAN bus gateway Driver",
            "1.0.0",
            "CAN via LAWICEL SLCAN over TCP (standard t frames);"
                    + " not SocketCAN / Peak-Vector SDK",
            "ISPF",
            Map.of(
                    "host", "127.0.0.1",
                    "port", "29536",
                    "timeoutMs", "3000"
            ),
            null,
            Set.of("read", "write")
    );

    private DriverObject driverObject;
    private String host = "127.0.0.1";
    private int port = 29536;
    private int timeoutMs = 3000;
    private Socket socket;
    private final Map<String, Integer> canIds = new ConcurrentHashMap<>();
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
                    "CAN SLCAN gateway connected to " + host + ":" + port
                            + " (not SocketCAN)");
        } catch (IOException e) {
            closeSocket();
            throw new DriverException("CAN gateway connect failed for " + host + ":" + port, e);
        }
    }

    @Override
    public void disconnect() {
        connected = false;
        canIds.clear();
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
            int canId = parseCanId(entry.getValue() == null || entry.getValue().isBlank()
                    ? pointId : entry.getValue());
            canIds.put(pointId, canId);
            String request = formatStandardFrame(canId, "");
            String response = transact(request);
            SlcanFrame frame = parseSlcanStandard(response);
            if (frame.canId() != (canId & 0x7FF)) {
                throw new DriverException("SLCAN returned id 0x"
                        + Integer.toHexString(frame.canId()) + " for 0x"
                        + Integer.toHexString(canId & 0x7FF));
            }
            String data = frame.dataHex();
            driverObject.updateVariable(pointId, DataRecord.single(VALUE_SCHEMA, Map.of(
                    "value", data,
                    "canId", formatCanId(canId),
                    "raw", response
            )));
        }
    }

    @Override
    public void writePoint(String pointId, DataRecord value) throws DriverException {
        ensureConnected();
        Integer mapped = canIds.get(pointId);
        int canId = mapped != null ? mapped : parseCanId(pointId);
        String data = normalizeHex(extractValue(value));
        String request = formatStandardFrame(canId, data);
        String response = transact(request);
        String trimmed = response.trim();
        if (!(trimmed.equalsIgnoreCase("z")
                || tryParseSlcanStandard(trimmed) != null)) {
            throw new DriverException("CAN SLCAN write rejected: " + response);
        }
        driverObject.updateVariable(pointId, DataRecord.single(VALUE_SCHEMA, Map.of(
                "value", data,
                "canId", formatCanId(canId),
                "raw", request
        )));
    }

    private synchronized String transact(String command) throws DriverException {
        try {
            writeAscii(socket.getOutputStream(), command);
            return readUntilCr(socket.getInputStream());
        } catch (IOException e) {
            throw new DriverException("CAN gateway I/O failed for " + host + ":" + port, e);
        }
    }

    /**
     * LAWICEL standard frame: id {@code 0x001}, DLC {@code 2}, data {@code 11 22}
     * → {@code t00121122\r}.
     */
    public static String formatId001Dlc2Data1122Literal() {
        return "t00121122\r";
    }

    static String formatStandardFrame(int canId, String dataHex) {
        String data = normalizeHex(dataHex);
        int dlc = data.length() / 2;
        if (dlc > 8) {
            throw new IllegalArgumentException("SLCAN DLC exceeds 8: " + dlc);
        }
        return "t"
                + String.format(Locale.ROOT, "%03X", canId & 0x7FF)
                + Integer.toHexString(dlc).toUpperCase(Locale.ROOT)
                + data
                + "\r";
    }

    static int parseCanId(String mapping) {
        String t = mapping.trim().toUpperCase(Locale.ROOT);
        if (t.startsWith("0X")) {
            t = t.substring(2);
        }
        if (t.isEmpty() || !t.matches("[0-9A-F]+")) {
            throw new IllegalArgumentException("Invalid CAN id: " + mapping);
        }
        int id = Integer.parseInt(t, 16);
        if (id < 0 || id > 0x7FF) {
            throw new IllegalArgumentException("Standard CAN id out of range: " + mapping);
        }
        return id;
    }

    static String formatCanId(int canId) {
        return String.format(Locale.ROOT, "%X", canId & 0x7FF);
    }

    static SlcanFrame parseSlcanStandard(String line) {
        SlcanFrame frame = tryParseSlcanStandard(line);
        if (frame == null) {
            throw new IllegalArgumentException("Invalid SLCAN standard frame: " + line);
        }
        return frame;
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

    static String normalizeHex(String raw) {
        if (raw == null || raw.isBlank()) {
            return "";
        }
        String hex = raw.trim().replace(" ", "").toUpperCase(Locale.ROOT);
        if (hex.startsWith("0X")) {
            hex = hex.substring(2);
        }
        if (!hex.matches("[0-9A-F]*") || (hex.length() % 2) != 0) {
            throw new IllegalArgumentException("Hex must be even-length: " + raw);
        }
        return hex;
    }

    private static String extractValue(DataRecord value) {
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
        return "";
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

    record SlcanFrame(int canId, String dataHex) {
    }
}
