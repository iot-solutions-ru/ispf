package com.ispf.driver.fatek;

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
 * Fatek FACON ASCII driver over a raw TCP socket (default port {@code 500}).
 * <p>
 * Published FACON framing (Fatek Communication Protocol Appendix):
 * <ul>
 *   <li>{@code STX} (02h) + station(2 hex ASCII) + command(2) + data + LRC(2 hex ASCII) + {@code ETX} (03h)</li>
 *   <li>LRC = low byte of the sum of ASCII codes of columns c～f (STX through data), as two hex digits</li>
 *   <li>Command {@code 46}: read continuous 16-bit registers — data {@code 01} + 6-char address
 *       (e.g. {@code R0} → {@code R00000})</li>
 *   <li>Command {@code 47}: write continuous 16-bit registers — data {@code 01} + address + 4-digit hex value</li>
 *   <li>Success response embeds error code {@code 0} immediately after the command code</li>
 * </ul>
 * Point mapping: {@code R0}, {@code D100}, {@code M10} (16-bit register form). Full STX…ETX frames are
 * accepted as explicit mappings.
 * <p>
 * Clean-room ISPF code, Apache-2.0 — JDK sockets only; no proprietary SDKs / PLC4X.
 */
public class FatekDeviceDriver implements DeviceDriver {

    static final char STX = 0x02;
    static final char ETX = 0x03;

    private static final Pattern REGISTER = Pattern.compile(
            "^(?<dev>[RDMXY])(?<addr>\\d+)$",
            Pattern.CASE_INSENSITIVE);

    private static final DataSchema VALUE_SCHEMA = DataSchema.builder("fatekValue")
            .field("value", FieldType.STRING)
            .field("register", FieldType.STRING)
            .field("command", FieldType.STRING)
            .build();

    private static final DriverMetadata METADATA = new DriverMetadata(
            "fatek",
            "Fatek FACON Driver",
            "0.1.0",
            "Fatek FACON ASCII command 46/47 register read-write over TCP",
            "ISPF",
            Map.of(
                    "host", "127.0.0.1",
                    "port", "500",
                    "station", "01",
                    "timeoutMs", "3000",
                    "pollIntervalMs", "5000"
            ),
            null,
            Set.of("read", "write")
    );

    private DriverObject driverObject;
    private String host = "127.0.0.1";
    private int port = 500;
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
            driverObject.log(DriverLogLevel.INFO, "Fatek FACON connected to " + host + ":" + port);
        } catch (IOException e) {
            closeSocket();
            throw new DriverException("Fatek FACON connect failed for " + host + ":" + port, e);
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
                throw new DriverException("Fatek FACON read rejected: " + printable(response));
            }
            String value = parseReadValue(response);
            driverObject.updateVariable(pointId, DataRecord.single(VALUE_SCHEMA, Map.of(
                    "value", value,
                    "register", registerFromMapping(mapping),
                    "command", printable(command)
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
            throw new DriverException("Fatek FACON write rejected: " + printable(response));
        }
        driverObject.updateVariable(pointId, DataRecord.single(VALUE_SCHEMA, Map.of(
                "value", payload,
                "register", registerFromMapping(mapping),
                "command", printable(command)
        )));
    }

    static String buildReadCommand(String station, String mapping) {
        String map = mapping == null ? "" : mapping.trim();
        if (map.isBlank()) {
            throw new IllegalArgumentException("Blank Fatek mapping");
        }
        if (map.indexOf(STX) >= 0) {
            return ensureFrame(map);
        }
        Matcher matcher = REGISTER.matcher(map);
        if (matcher.matches()) {
            String address = formatRegisterAddress(matcher.group("dev"), matcher.group("addr"));
            return frame(station, "46" + "01" + address);
        }
        throw new IllegalArgumentException("Unsupported Fatek point mapping: " + mapping);
    }

    static String buildWriteCommand(String station, String mapping, String payload) {
        String map = mapping == null ? "" : mapping.trim();
        String bodyValue = payload == null ? "" : payload.trim();
        if (map.contains("{value}")) {
            return ensureFrame(map.replace("{value}", bodyValue));
        }
        if (map.indexOf(STX) >= 0) {
            return ensureFrame(map);
        }
        Matcher matcher = REGISTER.matcher(map);
        if (matcher.matches()) {
            String address = formatRegisterAddress(matcher.group("dev"), matcher.group("addr"));
            String data = normalizeWriteHex(bodyValue);
            return frame(station, "47" + "01" + address + data);
        }
        throw new IllegalArgumentException("Unsupported Fatek write mapping: " + mapping);
    }

    static String registerFromMapping(String mapping) {
        String map = mapping == null ? "" : mapping.trim();
        Matcher matcher = REGISTER.matcher(map);
        if (matcher.matches()) {
            return matcher.group("dev").toUpperCase(Locale.ROOT) + Integer.parseInt(matcher.group("addr"));
        }
        Matcher embedded = Pattern.compile("([RDMXY]\\d+)", Pattern.CASE_INSENSITIVE).matcher(map);
        if (embedded.find()) {
            String token = embedded.group(1).toUpperCase(Locale.ROOT);
            Matcher parts = REGISTER.matcher(token);
            if (parts.matches()) {
                return parts.group("dev").toUpperCase(Locale.ROOT) + Integer.parseInt(parts.group("addr"));
            }
            return token;
        }
        return map.toUpperCase(Locale.ROOT).replace(String.valueOf(STX), "").replace(String.valueOf(ETX), "");
    }

    /**
     * Parses success response {@code STX&lt;st&gt;46 0 &lt;hhhh&gt;… LRC ETX} into a decimal string.
     */
    static String parseReadValue(String response) {
        if (response == null || response.isEmpty()) {
            return "";
        }
        String inner = stripStxEtx(response);
        if (inner.length() < 7) {
            return inner;
        }
        String body = inner.substring(0, inner.length() - 2);
        // station(2) + command(2) + error(1) + data…
        if (body.length() >= 5 && body.charAt(4) == '0') {
            String data = body.substring(5);
            if (data.length() >= 4 && data.matches("(?i)[0-9A-F]+")) {
                String firstWord = data.substring(0, 4);
                return String.valueOf(Integer.parseInt(firstWord, 16));
            }
            return data;
        }
        return inner;
    }

    static boolean isErrorResponse(String response) {
        if (response == null || response.isEmpty()) {
            return true;
        }
        String inner = stripStxEtx(response);
        if (inner.length() < 7) {
            return true;
        }
        String body = inner.substring(0, inner.length() - 2);
        return body.length() < 5 || body.charAt(4) != '0';
    }

    /**
     * Additive LRC over STX + station + command + data (Fatek columns c～f), as 2 ASCII hex digits.
     */
    static String lrc(String stationCommandAndData) {
        int sum = STX;
        for (int i = 0; i < stationCommandAndData.length(); i++) {
            sum += stationCommandAndData.charAt(i);
        }
        return String.format(Locale.ROOT, "%02X", sum & 0xFF);
    }

    static String frame(String station, String payloadWithoutStation) {
        String body = station + payloadWithoutStation;
        return STX + body + lrc(body) + ETX;
    }

    static String ensureFrame(String raw) {
        String map = raw;
        if (map.indexOf(STX) < 0) {
            throw new IllegalArgumentException("Fatek frame must contain STX");
        }
        String inner = stripStxEtx(map);
        if (inner.length() >= 3) {
            String maybeBody = inner.substring(0, inner.length() - 2);
            String maybeLrc = inner.substring(inner.length() - 2);
            if (maybeLrc.matches("[0-9A-Fa-f]{2}") && lrc(maybeBody).equalsIgnoreCase(maybeLrc)) {
                return STX + maybeBody + maybeLrc.toUpperCase(Locale.ROOT) + ETX;
            }
        }
        return STX + inner + lrc(inner) + ETX;
    }

    static String stripStxEtx(String frame) {
        String s = frame;
        int start = s.indexOf(STX);
        if (start >= 0) {
            s = s.substring(start + 1);
        }
        int end = s.indexOf(ETX);
        if (end >= 0) {
            s = s.substring(0, end);
        }
        return s;
    }

    static String printable(String frame) {
        return frame.replace(String.valueOf(STX), "<STX>").replace(String.valueOf(ETX), "<ETX>");
    }

    static String normalizeStation(String station) {
        String s = station.trim();
        if (s.matches("\\d")) {
            return "0" + s;
        }
        if (s.matches("[0-9A-Fa-f]{2}")) {
            return s.toUpperCase(Locale.ROOT);
        }
        throw new IllegalArgumentException("Fatek station must be 2 hex/decimal digits: " + station);
    }

    /** 16-bit FACON address: letter + 5 zero-padded digits ({@code R00000}, {@code D00100}). */
    static String formatRegisterAddress(String device, String addrDigits) {
        String letter = device.toUpperCase(Locale.ROOT);
        int addr = Integer.parseInt(addrDigits);
        return letter + String.format(Locale.ROOT, "%05d", addr);
    }

    static String normalizeWriteHex(String value) {
        if (value == null || value.isBlank()) {
            return "0000";
        }
        String v = value.trim();
        if (v.matches("(?i)0x[0-9a-f]+")) {
            int n = Integer.parseInt(v.substring(2), 16);
            return String.format(Locale.ROOT, "%04X", n & 0xFFFF);
        }
        if (v.matches("\\d+")) {
            int n = Integer.parseInt(v);
            return String.format(Locale.ROOT, "%04X", n & 0xFFFF);
        }
        if (v.matches("(?i)[0-9a-f]{1,4}")) {
            int n = Integer.parseInt(v, 16);
            return String.format(Locale.ROOT, "%04X", n & 0xFFFF);
        }
        throw new IllegalArgumentException("Fatek write value must be numeric: " + value);
    }

    private synchronized String transact(String command) throws DriverException {
        try {
            writeFrame(socket.getOutputStream(), command);
            return readFrame(socket.getInputStream());
        } catch (IOException e) {
            throw new DriverException(
                    "Fatek FACON I/O failed for " + host + ":" + port + " (" + printable(command) + ")", e);
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
        out.write(command.getBytes(StandardCharsets.US_ASCII));
        out.flush();
    }

    static String readFrame(InputStream in) throws IOException {
        ByteArrayOutputStream buf = new ByteArrayOutputStream();
        boolean seenStx = false;
        while (true) {
            int ch = in.read();
            if (ch < 0) {
                if (buf.size() == 0) {
                    throw new IOException("EOF reading Fatek FACON response");
                }
                break;
            }
            if (!seenStx) {
                if (ch == STX) {
                    seenStx = true;
                    buf.write(ch);
                }
                continue;
            }
            buf.write(ch);
            if (ch == ETX) {
                break;
            }
        }
        return buf.toString(StandardCharsets.US_ASCII);
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
