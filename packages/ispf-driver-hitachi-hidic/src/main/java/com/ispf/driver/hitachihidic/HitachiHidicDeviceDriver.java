package com.ispf.driver.hitachihidic;

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
 * Hitachi HIDIC / EH host-link ASCII lab driver over a raw TCP socket (default port {@code 3000}).
 * <p>
 * <strong>Lab dialect</strong> (host-link–shaped frames for EH-class register names — <em>not</em> a
 * full HIDIC-S10 / EH-150 / Ladder Editor proprietary stack):
 * <ul>
 *   <li>Frames: {@code @}{@station(2)}{@body}{@FCS(2 hex)}{@code *} + CR</li>
 *   <li>FCS = XOR of ASCII bytes of station+body</li>
 *   <li>Read WR (word register): body {@code RDWR} + 5-digit address → e.g. {@code WR100} →
 *       {@code @00RDWR00100XX*}</li>
 *   <li>Write WR: body {@code WDWR} + 5-digit address + 4-digit hex value</li>
 *   <li>Read R/M: body {@code RDR}/{@code RDM} + 5-digit address</li>
 *   <li>Write R/M: body {@code WDR}/{@code WDM} + 5-digit address + {@code 0000}/{@code 0001}</li>
 *   <li>Read response data: {@code @SSRD} + 4-digit hex + FCS + {@code *}</li>
 * </ul>
 * Point mapping (lab subset): {@code WR100}, {@code R100}, {@code M0}. Full {@code @…*} frames may
 * be sent as-is (FCS recomputed when a trailing {@code **} marker is present).
 * <p>
 * Clean-room ISPF code, Apache-2.0 — JDK sockets only; no Hitachi SDK / PLC4X / GPL stacks.
 */
public class HitachiHidicDeviceDriver implements DeviceDriver {

    private static final Pattern REGISTER = Pattern.compile(
            "^(?<dev>WR|[RM])(?<addr>\\d+)$",
            Pattern.CASE_INSENSITIVE);

    private static final DataSchema VALUE_SCHEMA = DataSchema.builder("hitachiHidicValue")
            .field("value", FieldType.STRING)
            .field("register", FieldType.STRING)
            .field("command", FieldType.STRING)
            .build();

    private static final DriverMetadata METADATA = new DriverMetadata(
            "hitachi-hidic",
            "Hitachi HIDIC Driver",
            "0.1.0",
            "Hitachi HIDIC/EH host-link ASCII lab (WR/R/M) over TCP — not full EH-150 stack",
            "ISPF",
            Map.of(
                    "host", "127.0.0.1",
                    "port", "3000",
                    "station", "00",
                    "timeoutMs", "3000",
                    "pollIntervalMs", "5000"
            ),
            null,
            Set.of("read", "write")
    );

    private DriverObject driverObject;
    private String host = "127.0.0.1";
    private int port = 3000;
    private String station = "00";
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
            case "station", "unitId", "node" -> station = normalizeStation(value.trim());
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
            driverObject.log(DriverLogLevel.INFO, "Hitachi HIDIC host-link connected to " + host + ":" + port);
        } catch (IOException e) {
            closeSocket();
            throw new DriverException("Hitachi HIDIC connect failed for " + host + ":" + port, e);
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
            String command = buildReadCommand(station, mapping);
            String response = transact(command);
            if (isErrorResponse(response)) {
                throw new DriverException("Hitachi HIDIC read rejected: " + response);
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
        String command = buildWriteCommand(station, mapping, payload);
        String response = transact(command);
        if (isErrorResponse(response)) {
            throw new DriverException("Hitachi HIDIC write rejected: " + response);
        }
        driverObject.updateVariable(pointId, DataRecord.single(VALUE_SCHEMA, Map.of(
                "value", payload,
                "register", registerFromMapping(mapping),
                "command", command
        )));
    }

    static String buildReadCommand(String station, String mapping) {
        String map = mapping == null ? "" : mapping.trim();
        if (map.isBlank()) {
            throw new IllegalArgumentException("Blank Hitachi HIDIC mapping");
        }
        if (map.startsWith("@")) {
            return ensureFcsFrame(map);
        }
        Matcher matcher = REGISTER.matcher(map);
        if (matcher.matches()) {
            String device = matcher.group("dev").toUpperCase(Locale.ROOT);
            int addr = Integer.parseInt(matcher.group("addr"));
            String padded = String.format(Locale.ROOT, "%05d", addr);
            String body = station + "RD" + device + padded;
            return "@" + body + fcs(body) + "*";
        }
        throw new IllegalArgumentException("Unsupported Hitachi HIDIC point mapping: " + mapping);
    }

    static String buildWriteCommand(String station, String mapping, String payload) {
        String map = mapping == null ? "" : mapping.trim();
        String bodyValue = payload == null ? "" : payload.trim();
        if (map.contains("{value}")) {
            return ensureFcsFrame(map.replace("{value}", bodyValue));
        }
        if (map.startsWith("@")) {
            return ensureFcsFrame(map);
        }
        Matcher matcher = REGISTER.matcher(map);
        if (matcher.matches()) {
            String device = matcher.group("dev").toUpperCase(Locale.ROOT);
            int addr = Integer.parseInt(matcher.group("addr"));
            String padded = String.format(Locale.ROOT, "%05d", addr);
            String data = normalizeWriteData(device, bodyValue);
            String body = station + "WD" + device + padded + data;
            return "@" + body + fcs(body) + "*";
        }
        throw new IllegalArgumentException("Unsupported Hitachi HIDIC write mapping: " + mapping);
    }

    static String registerFromMapping(String mapping) {
        String map = mapping == null ? "" : mapping.trim();
        Matcher matcher = REGISTER.matcher(map);
        if (matcher.matches()) {
            return matcher.group("dev").toUpperCase(Locale.ROOT) + Integer.parseInt(matcher.group("addr"));
        }
        Matcher embedded = Pattern.compile("(WR|[RM])(\\d{1,5})", Pattern.CASE_INSENSITIVE).matcher(map);
        if (embedded.find()) {
            return embedded.group(1).toUpperCase(Locale.ROOT) + Integer.parseInt(embedded.group(2));
        }
        return map.toUpperCase(Locale.ROOT);
    }

    /** Parses {@code @00RD0042XX*} success frames into a decimal string value. */
    static String parseReadValue(String response) {
        if (response == null) {
            return "";
        }
        String trimmed = stripTerminator(response.trim());
        if (trimmed.isEmpty() || isErrorResponse(trimmed)) {
            return trimmed;
        }
        Matcher matcher = Pattern.compile(
                "^@(\\d{2})RD([0-9A-Fa-f]{4})([0-9A-Fa-f]{2})$").matcher(trimmed);
        if (matcher.matches()) {
            int word = Integer.parseInt(matcher.group(2), 16);
            return String.valueOf(word);
        }
        Matcher bare = Pattern.compile(
                "^@(\\d{2})RD([0-9A-Fa-f]+)$").matcher(trimmed);
        if (bare.matches()) {
            String hex = bare.group(2);
            if (hex.length() >= 2) {
                hex = hex.substring(0, hex.length() - 2);
            }
            if (hex.isEmpty()) {
                return "0";
            }
            return String.valueOf(Integer.parseInt(hex, 16));
        }
        return trimmed;
    }

    static boolean isErrorResponse(String response) {
        if (response == null || response.isBlank()) {
            return false;
        }
        String t = stripTerminator(response.trim()).toUpperCase(Locale.ROOT);
        return t.contains("*E") || t.matches("@\\d{2}E.*") || t.startsWith("@??");
    }

    /** XOR FCS over station+command body (characters after {@code @}, before FCS). */
    static String fcs(String bodyWithoutAtAndFcs) {
        int xor = 0;
        for (int i = 0; i < bodyWithoutAtAndFcs.length(); i++) {
            xor ^= bodyWithoutAtAndFcs.charAt(i);
        }
        return String.format(Locale.ROOT, "%02X", xor & 0xFF);
    }

    static String ensureFcsFrame(String frame) {
        String trimmed = stripTerminator(frame.trim());
        if (!trimmed.startsWith("@")) {
            throw new IllegalArgumentException("Hitachi HIDIC frame must start with @: " + frame);
        }
        if (trimmed.endsWith("*")) {
            trimmed = trimmed.substring(0, trimmed.length() - 1);
        }
        String withoutAt = trimmed.substring(1);
        if (withoutAt.length() >= 3) {
            String maybeBody = withoutAt.substring(0, withoutAt.length() - 2);
            String maybeFcs = withoutAt.substring(withoutAt.length() - 2);
            if (maybeFcs.matches("[0-9A-Fa-f]{2}") && fcs(maybeBody).equalsIgnoreCase(maybeFcs)) {
                return "@" + maybeBody + maybeFcs + "*";
            }
            if (maybeFcs.equals("**") || maybeFcs.equals("??")) {
                return "@" + maybeBody + fcs(maybeBody) + "*";
            }
        }
        return "@" + withoutAt + fcs(withoutAt) + "*";
    }

    static String normalizeStation(String station) {
        String s = station.trim();
        if (s.matches("\\d")) {
            return "0" + s;
        }
        if (s.matches("\\d{2}")) {
            return s;
        }
        throw new IllegalArgumentException("Hitachi HIDIC station must be 2 decimal digits: " + station);
    }

    private static String normalizeWriteData(String device, String value) {
        if ("M".equals(device) || "R".equals(device)) {
            String v = value.isEmpty() ? "0" : value.trim();
            if ("1".equals(v) || "true".equalsIgnoreCase(v) || "on".equalsIgnoreCase(v)) {
                return "0001";
            }
            if ("0".equals(v) || "false".equalsIgnoreCase(v) || "off".equalsIgnoreCase(v)) {
                return "0000";
            }
            int n = Integer.parseInt(v);
            return String.format(Locale.ROOT, "%04X", n & 0xFFFF);
        }
        if (value.matches("(?i)0x[0-9a-f]+")) {
            int n = Integer.parseInt(value.substring(2), 16);
            return String.format(Locale.ROOT, "%04X", n & 0xFFFF);
        }
        if (value.matches("\\d+")) {
            int n = Integer.parseInt(value);
            return String.format(Locale.ROOT, "%04X", n & 0xFFFF);
        }
        if (value.matches("(?i)[0-9a-f]{1,4}")) {
            int n = Integer.parseInt(value, 16);
            return String.format(Locale.ROOT, "%04X", n & 0xFFFF);
        }
        throw new IllegalArgumentException("Hitachi HIDIC write value must be numeric: " + value);
    }

    private static String stripTerminator(String frame) {
        String t = frame;
        if (t.endsWith("\r") || t.endsWith("\n")) {
            t = t.substring(0, t.length() - 1);
        }
        if (t.endsWith("*")) {
            t = t.substring(0, t.length() - 1);
        }
        return t;
    }

    private synchronized String transact(String command) throws DriverException {
        try {
            writeFrame(socket.getOutputStream(), command);
            return readFrame(socket.getInputStream());
        } catch (IOException e) {
            throw new DriverException(
                    "Hitachi HIDIC I/O failed for " + host + ":" + port + " (" + command + ")", e);
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
                    throw new IOException("EOF reading Hitachi HIDIC response");
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
