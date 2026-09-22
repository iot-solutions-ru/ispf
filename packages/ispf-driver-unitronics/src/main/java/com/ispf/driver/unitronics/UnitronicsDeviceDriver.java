package com.ispf.driver.unitronics;

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
 * Unitronics PCOM ASCII driver over a raw TCP socket (default port {@code 20256}).
 * <p>
 * Published PCOM ASCII (Vision / UniStream serial ASCII subset):
 * <ul>
 *   <li>Frames: {@code /}{@unit(2)}{@cmd}{@params}{@FCS(2 hex)} + CR</li>
 *   <li>FCS = sum of ASCII bytes after {@code /}, modulo 256, as two uppercase hex digits</li>
 *   <li>Read MI: {@code /00RW} + addr(4 hex) + count(2 hex) — e.g. MI0×1 → {@code /00RW0000012A}</li>
 *   <li>Write MI: {@code /00SW} + addr + count + data(4 hex per word)</li>
 *   <li>Read MB: {@code /00RB} + addr(4 hex) + count(2 hex)</li>
 *   <li>Write MB: {@code /00SB} + addr + count + bit digits</li>
 * </ul>
 * Point mapping: {@code MI100}, {@code MB0}. Full {@code /…} frames may be sent as-is
 * (FCS recomputed when a trailing {@code **} marker is present).
 * <p>
 * Clean-room ISPF code, Apache-2.0 — JDK sockets only; no UniLogic SDK / PLC4X.
 */
public class UnitronicsDeviceDriver implements DeviceDriver {

    private static final Pattern REGISTER = Pattern.compile(
            "^(?<dev>MI|MB)(?<addr>\\d+)$",
            Pattern.CASE_INSENSITIVE);

    private static final DataSchema VALUE_SCHEMA = DataSchema.builder("unitronicsValue")
            .field("value", FieldType.STRING)
            .field("register", FieldType.STRING)
            .field("command", FieldType.STRING)
            .build();

    private static final DriverMetadata METADATA = new DriverMetadata(
            "unitronics",
            "Unitronics Driver",
            "0.1.0",
            "Unitronics PCOM ASCII (MI/MB read-write) over TCP",
            "ISPF",
            Map.of(
                    "host", "127.0.0.1",
                    "port", "20256",
                    "unitId", "00",
                    "timeoutMs", "3000",
                    "pollIntervalMs", "5000"
            ),
            null,
            Set.of("read", "write")
    );

    private DriverObject driverObject;
    private String host = "127.0.0.1";
    private int port = 20256;
    private String unitId = "00";
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
            case "unitId", "station", "id" -> unitId = normalizeUnit(value.trim());
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
            driverObject.log(DriverLogLevel.INFO, "Unitronics PCOM ASCII connected to " + host + ":" + port);
        } catch (IOException e) {
            closeSocket();
            throw new DriverException("Unitronics connect failed for " + host + ":" + port, e);
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
            String command = buildReadCommand(unitId, mapping);
            String response = transact(command);
            if (isErrorResponse(response)) {
                throw new DriverException("Unitronics PCOM read rejected: " + response);
            }
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
        String command = buildWriteCommand(unitId, mapping, payload);
        String response = transact(command);
        if (isErrorResponse(response)) {
            throw new DriverException("Unitronics PCOM write rejected: " + response);
        }
        driverObject.updateVariable(pointId, DataRecord.single(VALUE_SCHEMA, Map.of(
                "value", payload,
                "register", registerFromMapping(mapping),
                "command", command
        )));
    }

    static String buildReadCommand(String unitId, String mapping) {
        String map = mapping == null ? "" : mapping.trim();
        if (map.isBlank()) {
            throw new IllegalArgumentException("Blank Unitronics PCOM mapping");
        }
        if (map.startsWith("/")) {
            return ensureFcsFrame(map);
        }
        Matcher matcher = REGISTER.matcher(map);
        if (matcher.matches()) {
            String device = matcher.group("dev").toUpperCase(Locale.ROOT);
            int addr = Integer.parseInt(matcher.group("addr"));
            String cmd = "MI".equals(device) ? "RW" : "RB";
            String body = unitId + cmd
                    + String.format(Locale.ROOT, "%04X", addr & 0xFFFF)
                    + "01";
            return "/" + body + fcs(body);
        }
        throw new IllegalArgumentException("Unsupported Unitronics point mapping: " + mapping);
    }

    static String buildWriteCommand(String unitId, String mapping, String payload) {
        String map = mapping == null ? "" : mapping.trim();
        String bodyValue = payload == null ? "" : payload.trim();
        if (map.contains("{value}")) {
            return ensureFcsFrame(map.replace("{value}", bodyValue));
        }
        if (map.startsWith("/")) {
            return ensureFcsFrame(map);
        }
        Matcher matcher = REGISTER.matcher(map);
        if (matcher.matches()) {
            String device = matcher.group("dev").toUpperCase(Locale.ROOT);
            int addr = Integer.parseInt(matcher.group("addr"));
            String data = normalizeWriteData(device, bodyValue);
            String cmd = "MI".equals(device) ? "SW" : "SB";
            String body = unitId + cmd
                    + String.format(Locale.ROOT, "%04X", addr & 0xFFFF)
                    + "01"
                    + data;
            return "/" + body + fcs(body);
        }
        throw new IllegalArgumentException("Unsupported Unitronics write mapping: " + mapping);
    }

    static String registerFromMapping(String mapping) {
        String map = mapping == null ? "" : mapping.trim();
        Matcher matcher = REGISTER.matcher(map);
        if (matcher.matches()) {
            return matcher.group("dev").toUpperCase(Locale.ROOT) + Integer.parseInt(matcher.group("addr"));
        }
        Matcher embedded = Pattern.compile("(MI|MB)(\\d+)", Pattern.CASE_INSENSITIVE).matcher(map);
        if (embedded.find()) {
            return embedded.group(1).toUpperCase(Locale.ROOT) + Integer.parseInt(embedded.group(2));
        }
        return map.toUpperCase(Locale.ROOT);
    }

    /**
     * Parses {@code /A}{@unit}{@CC}{@data}{@FCS} success frames into a decimal/string value.
     */
    static String parseReadValue(String response) {
        if (response == null) {
            return "";
        }
        String trimmed = response.trim();
        if (trimmed.isEmpty() || isErrorResponse(trimmed)) {
            return trimmed;
        }
        Matcher matcher = Pattern.compile(
                "^/A(\\d{2})([A-Za-z]{2})([0-9A-Fa-f]*)([0-9A-Fa-f]{2})$").matcher(trimmed);
        if (matcher.matches()) {
            String cc = matcher.group(2).toUpperCase(Locale.ROOT);
            String data = matcher.group(3);
            if ("RW".equals(cc) || "GF".equals(cc) || "GT".equals(cc) || "GP".equals(cc)) {
                if (data.length() >= 4) {
                    return String.valueOf(Integer.parseInt(data.substring(0, 4), 16));
                }
                return data.isEmpty() ? "0" : data;
            }
            if ("RB".equals(cc) || "RE".equals(cc) || "RA".equals(cc) || "GS".equals(cc)) {
                if (data.isEmpty()) {
                    return "0";
                }
                return data.substring(0, 1);
            }
            return data;
        }
        // Write ACK /A00SWXX — no payload
        if (trimmed.matches("^/A\\d{2}[A-Za-z]{2}[0-9A-Fa-f]{2}$")) {
            return "";
        }
        return trimmed;
    }

    static boolean isErrorResponse(String response) {
        if (response == null || response.isBlank()) {
            return false;
        }
        String t = response.trim().toUpperCase(Locale.ROOT);
        return t.startsWith("/N") || t.startsWith("/E") || t.contains("ERR");
    }

    /** PCOM ASCII FCS: sum of characters after {@code /}, modulo 256, as two hex digits. */
    static String fcs(String bodyWithoutSlash) {
        int sum = 0;
        for (int i = 0; i < bodyWithoutSlash.length(); i++) {
            sum = (sum + bodyWithoutSlash.charAt(i)) & 0xFF;
        }
        return String.format(Locale.ROOT, "%02X", sum);
    }

    static String ensureFcsFrame(String frame) {
        String trimmed = frame.trim();
        if (trimmed.endsWith("\r")) {
            trimmed = trimmed.substring(0, trimmed.length() - 1);
        }
        if (!trimmed.startsWith("/")) {
            throw new IllegalArgumentException("Unitronics PCOM frame must start with /: " + frame);
        }
        String withoutSlash = trimmed.substring(1);
        if (withoutSlash.length() >= 3) {
            String maybeBody = withoutSlash.substring(0, withoutSlash.length() - 2);
            String maybeFcs = withoutSlash.substring(withoutSlash.length() - 2);
            if (maybeFcs.matches("[0-9A-Fa-f]{2}") && fcs(maybeBody).equalsIgnoreCase(maybeFcs)) {
                return "/" + maybeBody + maybeFcs;
            }
            if (maybeFcs.equals("**") || maybeFcs.equals("??")) {
                return "/" + maybeBody + fcs(maybeBody);
            }
        }
        return "/" + withoutSlash + fcs(withoutSlash);
    }

    static String normalizeUnit(String unit) {
        String s = unit.trim();
        if (s.matches("\\d")) {
            return "0" + s;
        }
        if (s.matches("\\d{2}")) {
            return s;
        }
        throw new IllegalArgumentException("Unitronics unitId must be 2 decimal digits: " + unit);
    }

    private static String normalizeWriteData(String device, String value) {
        if ("MB".equals(device)) {
            String v = value.isEmpty() ? "0" : value.trim();
            if ("1".equals(v) || "true".equalsIgnoreCase(v) || "on".equalsIgnoreCase(v)) {
                return "1";
            }
            if ("0".equals(v) || "false".equalsIgnoreCase(v) || "off".equalsIgnoreCase(v)) {
                return "0";
            }
            return String.valueOf(Integer.parseInt(v) != 0 ? 1 : 0);
        }
        int n;
        if (value.matches("(?i)0x[0-9a-f]+")) {
            n = Integer.parseInt(value.substring(2), 16);
        } else if (value.matches("-?\\d+")) {
            n = Integer.parseInt(value);
        } else if (value.matches("(?i)[0-9a-f]{1,4}")) {
            n = Integer.parseInt(value, 16);
        } else {
            throw new IllegalArgumentException("Unitronics write value must be numeric: " + value);
        }
        return String.format(Locale.ROOT, "%04X", n & 0xFFFF);
    }

    private synchronized String transact(String command) throws DriverException {
        try {
            writeFrame(socket.getOutputStream(), command);
            return readFrame(socket.getInputStream());
        } catch (IOException e) {
            throw new DriverException(
                    "Unitronics PCOM I/O failed for " + host + ":" + port + " (" + command + ")", e);
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
        String frame = command.endsWith("\r") ? command : command + "\r";
        out.write(frame.getBytes(StandardCharsets.US_ASCII));
        out.flush();
    }

    static String readFrame(InputStream in) throws IOException {
        ByteArrayOutputStream line = new ByteArrayOutputStream();
        while (true) {
            int ch = in.read();
            if (ch < 0) {
                if (line.size() == 0) {
                    throw new IOException("EOF reading Unitronics PCOM response");
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
