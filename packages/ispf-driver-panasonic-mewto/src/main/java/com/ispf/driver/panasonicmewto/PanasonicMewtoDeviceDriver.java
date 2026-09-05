package com.ispf.driver.panasonicmewto;

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
 * Panasonic MEWTOCOL-COM driver — ASCII MEWTOCOL frames over a raw TCP socket.
 * <p>
 * Point mapping (lab subset):
 * <ul>
 *   <li>{@code D100}, {@code R0} — expanded to {@code %&lt;station&gt;#RDD…}/{@code #RCC…} with BCC + CR</li>
 *   <li>Full frames starting with {@code %} — sent as-is (BCC recomputed when the body lacks a trailing
 *       2-hex BCC before CR)</li>
 *   <li>Writes use {@code #WDD}/{@code #WCC} with record field {@code value}</li>
 * </ul>
 * Default TCP port {@code 9094}, station {@code 01}. BCC is XOR of ASCII bytes from station through
 * command body (standard MEWTOCOL-COM shape). This is a clean-room lab subset — not FPWIN / proprietary.
 * <p>
 * Clean-room ISPF code, Apache-2.0 — JDK sockets only; no proprietary SDKs / PLC4X.
 */
public class PanasonicMewtoDeviceDriver implements DeviceDriver {

    private static final Pattern REGISTER = Pattern.compile(
            "^(?<dev>[DR])(?<addr>\\d+)$",
            Pattern.CASE_INSENSITIVE);

    private static final DataSchema VALUE_SCHEMA = DataSchema.builder("panasonicMewtoValue")
            .field("value", FieldType.STRING)
            .field("register", FieldType.STRING)
            .field("command", FieldType.STRING)
            .build();

    private static final DriverMetadata METADATA = new DriverMetadata(
            "panasonic-mewto",
            "Panasonic MEWTOCOL Driver",
            "0.1.0",
            "Reads/writes Panasonic MEWTOCOL-COM ASCII D/R registers over TCP",
            "ISPF",
            Map.of(
                    "host", "127.0.0.1",
                    "port", "9094",
                    "station", "01",
                    "timeoutMs", "3000",
                    "pollIntervalMs", "5000"
            ),
            null,
            Set.of("read", "write")
    );

    private DriverObject driverObject;
    private String host = "127.0.0.1";
    private int port = 9094;
    private String station = "01";
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
            case "station" -> station = normalizeStation(value.trim());
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
            driverObject.log(DriverLogLevel.INFO, "Panasonic MEWTOCOL connected to " + host + ":" + port);
        } catch (IOException e) {
            closeSocket();
            throw new DriverException("Panasonic MEWTOCOL connect failed for " + host + ":" + port, e);
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
            throw new DriverException("Panasonic MEWTOCOL write rejected: " + response);
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
            throw new IllegalArgumentException("Blank MEWTOCOL mapping");
        }
        if (map.startsWith("%")) {
            return ensureBccFrame(map);
        }
        Matcher matcher = REGISTER.matcher(map);
        if (matcher.matches()) {
            String device = matcher.group("dev").toUpperCase(Locale.ROOT);
            int addr = Integer.parseInt(matcher.group("addr"));
            String padded = String.format(Locale.ROOT, "%05d", addr);
            String body = station + "#" + ("D".equals(device) ? "RDD" : "RCC") + padded + padded;
            return "%" + body + bcc(body);
        }
        return ensureBccFrame(map.startsWith("%") ? map : "%" + station + map);
    }

    static String buildWriteCommand(String station, String mapping, String payload) {
        String map = mapping == null ? "" : mapping.trim();
        String bodyValue = payload == null ? "" : payload.trim();
        if (map.contains("{value}")) {
            return ensureBccFrame(map.replace("{value}", bodyValue));
        }
        if (map.startsWith("%") && (map.contains("#WD") || map.contains("#WC") || map.contains("#WR"))) {
            return ensureBccFrame(map);
        }
        Matcher matcher = REGISTER.matcher(map);
        if (matcher.matches()) {
            String device = matcher.group("dev").toUpperCase(Locale.ROOT);
            int addr = Integer.parseInt(matcher.group("addr"));
            String padded = String.format(Locale.ROOT, "%05d", addr);
            String data = normalizeWriteData(device, bodyValue);
            String body = station + "#" + ("D".equals(device) ? "WDD" : "WCC") + padded + padded + data;
            return "%" + body + bcc(body);
        }
        // Expand short register embedded in a prior read-style mapping
        Matcher embedded = Pattern.compile("([DR]\\d+)", Pattern.CASE_INSENSITIVE).matcher(map);
        if (embedded.find()) {
            return buildWriteCommand(station, embedded.group(1), bodyValue);
        }
        String body = station + "#WDD" + bodyValue;
        return "%" + body + bcc(body);
    }

    static String registerFromMapping(String mapping) {
        String map = mapping == null ? "" : mapping.trim();
        Matcher matcher = REGISTER.matcher(map);
        if (matcher.matches()) {
            return matcher.group("dev").toUpperCase(Locale.ROOT) + Integer.parseInt(matcher.group("addr"));
        }
        Matcher embedded = Pattern.compile("([DR])(\\d{1,5})", Pattern.CASE_INSENSITIVE).matcher(map);
        if (embedded.find()) {
            return embedded.group(1).toUpperCase(Locale.ROOT) + Integer.parseInt(embedded.group(2));
        }
        Matcher addr = Pattern.compile("#R[DC]{2}(\\d{5})", Pattern.CASE_INSENSITIVE).matcher(map);
        if (addr.find()) {
            return "D" + Integer.parseInt(addr.group(1));
        }
        return map.toUpperCase(Locale.ROOT);
    }

    /**
     * Parses {@code %01$RD…value…BB} / {@code %01$RC…} success frames into a numeric/string value.
     * Trailing 2-hex BCC is always stripped when present (lab dialect).
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
                "^%\\d{2}\\$(RD|RC)(=?)(.*)$",
                Pattern.CASE_INSENSITIVE).matcher(trimmed);
        if (matcher.matches()) {
            String dataAndBcc = matcher.group(3);
            if (dataAndBcc.length() >= 2
                    && dataAndBcc.substring(dataAndBcc.length() - 2).matches("[0-9A-Fa-f]{2}")) {
                return dataAndBcc.substring(0, dataAndBcc.length() - 2);
            }
            return dataAndBcc.isEmpty() ? "0" : dataAndBcc;
        }
        return trimmed;
    }

    static boolean isErrorResponse(String response) {
        if (response == null || response.isBlank()) {
            return false;
        }
        String t = response.trim().toUpperCase(Locale.ROOT);
        return t.contains("!UE") || t.contains("!ER") || t.matches("%\\d{2}!.*");
    }

    /** XOR BCC over station+command body (characters after {@code %}, before BCC). */
    static String bcc(String bodyWithoutPercentAndBcc) {
        int xor = 0;
        for (int i = 0; i < bodyWithoutPercentAndBcc.length(); i++) {
            xor ^= bodyWithoutPercentAndBcc.charAt(i);
        }
        return String.format(Locale.ROOT, "%02X", xor & 0xFF);
    }

    static String ensureBccFrame(String frame) {
        String trimmed = frame.trim();
        if (trimmed.endsWith("\r")) {
            trimmed = trimmed.substring(0, trimmed.length() - 1);
        }
        if (!trimmed.startsWith("%")) {
            throw new IllegalArgumentException("MEWTOCOL frame must start with %: " + frame);
        }
        String withoutPercent = trimmed.substring(1);
        // If already looks complete with BCC (body + 2 hex), recompute from body without last 2
        if (withoutPercent.length() >= 3) {
            String maybeBody = withoutPercent.substring(0, withoutPercent.length() - 2);
            String maybeBcc = withoutPercent.substring(withoutPercent.length() - 2);
            if (maybeBcc.matches("[0-9A-Fa-f]{2}") && bcc(maybeBody).equalsIgnoreCase(maybeBcc)) {
                return "%" + maybeBody + maybeBcc;
            }
            // Trailing ** often used as placeholder BCC in docs — replace with real BCC
            if (maybeBcc.equals("**")) {
                return "%" + maybeBody + bcc(maybeBody);
            }
        }
        return "%" + withoutPercent + bcc(withoutPercent);
    }

    static String normalizeStation(String station) {
        String s = station.trim();
        if (s.matches("\\d")) {
            return "0" + s;
        }
        if (s.matches("\\d{2}")) {
            return s;
        }
        throw new IllegalArgumentException("MEWTOCOL station must be 2 decimal digits: " + station);
    }

    private static String normalizeWriteData(String device, String value) {
        if ("R".equals(device) || "C".equals(device)) {
            return value.isEmpty() ? "0" : value;
        }
        // D-area lab dialect: decimal digits as ASCII payload (fake accepts this consistently)
        if (value.matches("\\d+")) {
            return value;
        }
        return value;
    }

    private synchronized String transact(String command) throws DriverException {
        try {
            writeFrame(socket.getOutputStream(), command);
            return readFrame(socket.getInputStream());
        } catch (IOException e) {
            throw new DriverException(
                    "Panasonic MEWTOCOL I/O failed for " + host + ":" + port + " (" + command + ")", e);
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
                    throw new IOException("EOF reading MEWTOCOL response");
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
