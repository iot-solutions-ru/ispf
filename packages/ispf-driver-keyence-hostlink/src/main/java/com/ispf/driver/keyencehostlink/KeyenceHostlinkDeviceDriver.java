package com.ispf.driver.keyencehostlink;

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
 * Keyence Host Link driver — ASCII Host Link / KV-style commands over a raw TCP socket.
 * <p>
 * Point mapping (lab subset):
 * <ul>
 *   <li>{@code DM100}, {@code R0}, {@code EM10} — expanded to {@code RDS &lt;device&gt;&lt;addr&gt; 1}</li>
 *   <li>{@code RDS DM100 1} — sent as-is (read)</li>
 *   <li>Writes use {@code WR &lt;device&gt;&lt;addr&gt; &lt;value&gt;} from the mapped register and record
 *       field {@code value} (also accepts {@code payload}/{@code data})</li>
 * </ul>
 * Default TCP port {@code 8501}. Frames are CR-terminated. This is a clean-room lab subset of
 * public Host Link ASCII shapes (RDS/WR/WRS) — not a full KV Studio / proprietary stack.
 * <p>
 * Clean-room ISPF code, Apache-2.0 — JDK sockets only; no proprietary SDKs / PLC4X.
 */
public class KeyenceHostlinkDeviceDriver implements DeviceDriver {

    private static final Pattern REGISTER = Pattern.compile(
            "^(?<dev>CTH|CTC|CR|MR|LR|DM|EM|FM|ZF|W|R|B|T|C)(?<addr>\\d+)$",
            Pattern.CASE_INSENSITIVE);

    private static final DataSchema VALUE_SCHEMA = DataSchema.builder("keyenceHostlinkValue")
            .field("value", FieldType.STRING)
            .field("register", FieldType.STRING)
            .field("command", FieldType.STRING)
            .build();

    private static final DriverMetadata METADATA = new DriverMetadata(
            "keyence-hostlink",
            "Keyence Host Link Driver",
            "0.1.0",
            "Reads/writes Keyence Host Link ASCII registers (RDS/WR) over TCP",
            "ISPF",
            Map.of(
                    "host", "127.0.0.1",
                    "port", "8501",
                    "timeoutMs", "3000",
                    "pollIntervalMs", "5000"
            ),
            null,
            Set.of("read", "write")
    );

    private DriverObject driverObject;
    private String host = "127.0.0.1";
    private int port = 8501;
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
            driverObject.log(DriverLogLevel.INFO, "Keyence Host Link connected to " + host + ":" + port);
        } catch (IOException e) {
            closeSocket();
            throw new DriverException("Keyence Host Link connect failed for " + host + ":" + port, e);
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
            String command = buildReadCommand(mapping);
            String response = transact(command);
            String value = parseReadValue(response);
            driverObject.updateVariable(pointId, DataRecord.single(VALUE_SCHEMA, Map.of(
                    "value", value,
                    "register", registerFromMapping(mapping),
                    "command", command
            )));
        }
    }

    @Override
    public void writePoint(String pointId, DataRecord value) throws DriverException {
        if (!isConnected()) {
            throw new DriverException("Not connected");
        }
        String mapping = points.getOrDefault(pointId, pointId);
        String payload = extractValue(value);
        String command = buildWriteCommand(mapping, payload);
        String response = transact(command);
        if (isErrorResponse(response)) {
            throw new DriverException("Keyence Host Link write rejected: " + response);
        }
        driverObject.updateVariable(pointId, DataRecord.single(VALUE_SCHEMA, Map.of(
                "value", payload,
                "register", registerFromMapping(mapping),
                "command", command
        )));
    }

    static String buildReadCommand(String mapping) {
        String map = mapping == null ? "" : mapping.trim();
        if (map.isBlank()) {
            throw new IllegalArgumentException("Blank Keyence Host Link mapping");
        }
        String upper = map.toUpperCase(Locale.ROOT);
        if (upper.startsWith("RDS ") || upper.startsWith("RD ") || upper.startsWith("WRS ") || upper.startsWith("WR ")) {
            return map;
        }
        Matcher matcher = REGISTER.matcher(map);
        if (matcher.matches()) {
            String device = matcher.group("dev").toUpperCase(Locale.ROOT);
            String addr = matcher.group("addr");
            return "RDS " + device + addr + " 1";
        }
        return map;
    }

    static String buildWriteCommand(String mapping, String payload) {
        String map = mapping == null ? "" : mapping.trim();
        String body = payload == null ? "" : payload.trim();
        if (map.contains("{value}")) {
            return map.replace("{value}", body);
        }
        String upper = map.toUpperCase(Locale.ROOT);
        if (upper.startsWith("WRS ") || upper.startsWith("WR ")) {
            if (upper.matches("WRS?\\s+\\S+\\s+\\d+")) {
                return map + " " + body;
            }
            return map;
        }
        Matcher matcher = REGISTER.matcher(map);
        if (matcher.matches()) {
            String device = matcher.group("dev").toUpperCase(Locale.ROOT);
            String addr = matcher.group("addr");
            return "WR " + device + addr + " " + body;
        }
        if (upper.startsWith("RDS ")) {
            String rest = map.substring(4).trim();
            String[] parts = rest.split("\\s+");
            if (parts.length >= 1) {
                return "WR " + parts[0] + " " + body;
            }
        }
        return "WR " + map + " " + body;
    }

    static String registerFromMapping(String mapping) {
        String map = mapping == null ? "" : mapping.trim();
        Matcher matcher = REGISTER.matcher(map);
        if (matcher.matches()) {
            return matcher.group("dev").toUpperCase(Locale.ROOT) + matcher.group("addr");
        }
        String upper = map.toUpperCase(Locale.ROOT);
        if (upper.startsWith("RDS ") || upper.startsWith("RD ") || upper.startsWith("WRS ") || upper.startsWith("WR ")) {
            String[] parts = map.split("\\s+");
            if (parts.length >= 2) {
                return parts[1].toUpperCase(Locale.ROOT);
            }
        }
        return map.toUpperCase(Locale.ROOT);
    }

    static String parseReadValue(String response) {
        if (response == null) {
            return "";
        }
        String trimmed = response.trim();
        if (trimmed.isEmpty() || isErrorResponse(trimmed)) {
            return trimmed;
        }
        if (trimmed.equalsIgnoreCase("OK")) {
            return trimmed;
        }
        String[] parts = trimmed.split("\\s+");
        if (parts.length >= 2 && REGISTER.matcher(parts[0]).matches()) {
            return parts[1];
        }
        return parts[parts.length - 1];
    }

    static boolean isErrorResponse(String response) {
        if (response == null || response.isBlank()) {
            return false;
        }
        String t = response.trim().toUpperCase(Locale.ROOT);
        return t.startsWith("E") && t.length() <= 4 && t.chars().skip(1).allMatch(Character::isDigit);
    }

    private synchronized String transact(String command) throws DriverException {
        try {
            writeFrame(socket.getOutputStream(), command);
            return readFrame(socket.getInputStream());
        } catch (IOException e) {
            throw new DriverException(
                    "Keyence Host Link I/O failed for " + host + ":" + port + " (" + command + ")", e);
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

    static void writeFrame(OutputStream out, String command) throws IOException {
        out.write((command.trim() + "\r").getBytes(StandardCharsets.US_ASCII));
        out.flush();
    }

    static String readFrame(InputStream in) throws IOException {
        ByteArrayOutputStream line = new ByteArrayOutputStream();
        while (true) {
            int ch = in.read();
            if (ch < 0) {
                if (line.size() == 0) {
                    throw new IOException("EOF reading Keyence Host Link response");
                }
                break;
            }
            if (ch == '\r' || ch == '\n') {
                if (line.size() == 0) {
                    continue;
                }
                break;
            }
            line.write(ch);
        }
        return line.toString(StandardCharsets.US_ASCII).trim();
    }

    private static String extractValue(DataRecord value) {
        if (value == null || value.rowCount() == 0) {
            return "";
        }
        Map<String, Object> row = value.firstRow();
        for (String key : List.of("value", "payload", "data", "text", "raw")) {
            Object candidate = row.get(key);
            if (candidate != null) {
                return String.valueOf(candidate);
            }
        }
        if (row.size() == 1) {
            return String.valueOf(row.values().iterator().next());
        }
        return row.toString();
    }
}
