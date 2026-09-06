package com.ispf.driver.j1939;

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
 * SAE J1939 driver — lab TCP gateway dialect (default port {@code 29536}).
 * <p>
 * Honesty boundary: this talks to an ISPF J1939-over-TCP gateway lab, not raw SocketCAN,
 * not an ISO 15765-2 (ISO-TP) kernel stack, and not Vector/Peak proprietary CAN SDKs.
 * Frames are line-oriented ASCII:
 * <pre>
 *   PGN,SA,DATA_HEX{@code \\n}
 * </pre>
 * Optional request/response helpers used by the lab gateway:
 * <pre>
 *   GET &lt;pgn-mapping&gt;{@code \\n}  →  PGN,SA,DATA_HEX{@code \\n}
 *   SET PGN,SA,DATA_HEX{@code \\n} →  OK{@code \\n}
 * </pre>
 * Point mappings accept {@code PGN:61444}, {@code 0xF004}, {@code 61444}, or {@code PGN:0xF004}.
 * Reads expose last payload fields {@code value}/{@code data}/{@code sa} (plus {@code pgn}).
 * Writes send a PGN frame; record {@code value}/{@code data} may be hex or a numeric decimal
 * converted to hex; optional {@code sa} overrides the configured source address.
 * <p>
 * Clean-room ISPF code, Apache-2.0 — JDK sockets only.
 */
public class J1939DeviceDriver implements DeviceDriver {

    private static final Pattern PGN_MAPPING = Pattern.compile(
            "^(?:PGN[:\\s-]*)?(?:0x)?([0-9A-Fa-f]+)$",
            Pattern.CASE_INSENSITIVE);

    private static final Pattern FRAME_LINE = Pattern.compile(
            "^\\s*(?:PGN[:\\s-]*)?(?:0x)?([0-9A-Fa-f]+)\\s*,\\s*(?:0x)?([0-9A-Fa-f]+)\\s*,\\s*([0-9A-Fa-f]*)\\s*$",
            Pattern.CASE_INSENSITIVE);

    private static final DataSchema FRAME_SCHEMA = DataSchema.builder("j1939Frame")
            .field("value", FieldType.STRING)
            .field("data", FieldType.STRING)
            .field("sa", FieldType.STRING)
            .field("pgn", FieldType.STRING)
            .build();

    private static final DriverMetadata METADATA = new DriverMetadata(
            "j1939",
            "SAE J1939 Driver",
            "0.1.0",
            "SAE J1939 over TCP gateway lab (line frames PGN,SA,DATA_HEX) — not SocketCAN / ISO-TP",
            "ISPF",
            Map.of(
                    "host", "127.0.0.1",
                    "port", "29536",
                    "sa", "0",
                    "timeoutMs", "3000",
                    "pollIntervalMs", "5000"
            ),
            null,
            Set.of("read", "write")
    );

    private DriverObject driverObject;
    private String host = "127.0.0.1";
    private int port = 29536;
    private int defaultSa = 0;
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
            case "sa", "sourceAddress" -> defaultSa = parseIntFlexible(value.trim());
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
                    "J1939 TCP gateway lab connected to " + host + ":" + port
                            + " (not SocketCAN / ISO-TP)");
        } catch (IOException e) {
            closeSocket();
            throw new DriverException("J1939 TCP gateway connect failed for " + host + ":" + port, e);
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
            int pgn = parsePgnMapping(mapping);
            Frame frame = getFrame(pgn);
            driverObject.updateVariable(pointId, DataRecord.single(FRAME_SCHEMA, Map.of(
                    "value", frame.dataHex,
                    "data", frame.dataHex,
                    "sa", Integer.toString(frame.sa),
                    "pgn", Integer.toString(frame.pgn)
            )));
        }
    }

    @Override
    public void writePoint(String pointId, DataRecord value) throws DriverException {
        if (!isConnected()) {
            throw new DriverException("Not connected");
        }
        String mapping = points.getOrDefault(pointId, pointId);
        int pgn = parsePgnMapping(mapping);
        int sa = extractSa(value, defaultSa);
        String dataHex = extractDataHex(value);
        setFrame(pgn, sa, dataHex);
        driverObject.updateVariable(pointId, DataRecord.single(FRAME_SCHEMA, Map.of(
                "value", dataHex,
                "data", dataHex,
                "sa", Integer.toString(sa),
                "pgn", Integer.toString(pgn)
        )));
    }

    private Frame getFrame(int pgn) throws DriverException {
        String response = transact("GET " + formatPgn(pgn));
        Frame frame = parseFrameLine(response);
        if (frame.pgn != pgn) {
            throw new DriverException("J1939 gateway returned PGN " + frame.pgn + " for request " + pgn);
        }
        return frame;
    }

    private void setFrame(int pgn, int sa, String dataHex) throws DriverException {
        String line = formatFrame(pgn, sa, dataHex);
        String response = transact("SET " + line);
        if (!response.toUpperCase(Locale.ROOT).startsWith("OK")) {
            // Accept echo of the frame as success for push-style gateways.
            Frame echoed = tryParseFrameLine(response);
            if (echoed == null || echoed.pgn != pgn) {
                throw new DriverException("J1939 gateway write rejected: " + response);
            }
        }
    }

    private synchronized String transact(String command) throws DriverException {
        try {
            writeLine(socket.getOutputStream(), command);
            return readLine(socket.getInputStream());
        } catch (IOException e) {
            throw new DriverException(
                    "J1939 TCP gateway I/O failed for " + host + ":" + port + " (" + command + ")", e);
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

    static int parsePgnMapping(String mapping) {
        if (mapping == null || mapping.isBlank()) {
            throw new IllegalArgumentException("Blank J1939 PGN mapping");
        }
        String trimmed = mapping.trim();
        Matcher matcher = PGN_MAPPING.matcher(trimmed);
        if (!matcher.matches()) {
            throw new IllegalArgumentException("Unsupported J1939 mapping (expected PGN:61444 or 0xF004): " + mapping);
        }
        String digits = matcher.group(1);
        int pgn = digits.matches("(?i)0*[0-9A-F]*[A-F][0-9A-F]*") || trimmed.toLowerCase(Locale.ROOT).contains("0x")
                ? Integer.parseInt(digits, 16)
                : Integer.parseInt(digits, 10);
        if (pgn < 0 || pgn > 0x3FFFF) {
            throw new IllegalArgumentException("J1939 PGN out of range: " + pgn);
        }
        return pgn;
    }

    static Frame parseFrameLine(String line) {
        Frame frame = tryParseFrameLine(line);
        if (frame == null) {
            throw new IllegalArgumentException("Invalid J1939 frame line: " + line);
        }
        return frame;
    }

    static Frame tryParseFrameLine(String line) {
        if (line == null) {
            return null;
        }
        String trimmed = line.trim();
        if (trimmed.regionMatches(true, 0, "OK", 0, 2) && trimmed.length() > 2) {
            trimmed = trimmed.substring(2).trim();
            if (trimmed.startsWith(",")) {
                trimmed = trimmed.substring(1).trim();
            }
        }
        Matcher matcher = FRAME_LINE.matcher(trimmed);
        if (!matcher.matches()) {
            return null;
        }
        int pgn = parseIntFlexible(matcher.group(1));
        int sa = parseIntFlexible(matcher.group(2));
        String data = normalizeHex(matcher.group(3));
        return new Frame(pgn, sa, data);
    }

    static String formatFrame(int pgn, int sa, String dataHex) {
        return formatPgn(pgn) + "," + sa + "," + normalizeHex(dataHex);
    }

    static String formatPgn(int pgn) {
        return Integer.toString(pgn);
    }

    static String normalizeHex(String raw) {
        if (raw == null || raw.isBlank()) {
            return "";
        }
        String hex = raw.trim();
        if (hex.regionMatches(true, 0, "0x", 0, 2)) {
            hex = hex.substring(2);
        }
        hex = hex.replace(" ", "").toUpperCase(Locale.ROOT);
        if (!hex.matches("[0-9A-F]*") || (hex.length() % 2) != 0) {
            throw new IllegalArgumentException("DATA_HEX must be even-length hex: " + raw);
        }
        return hex;
    }

    static String extractDataHex(DataRecord value) {
        if (value == null || value.rowCount() == 0) {
            return "";
        }
        Map<String, Object> row = value.firstRow();
        for (String key : List.of("data", "value", "payload", "hex", "raw")) {
            Object candidate = row.get(key);
            if (candidate != null) {
                return toDataHex(candidate);
            }
        }
        if (row.size() == 1) {
            return toDataHex(row.values().iterator().next());
        }
        throw new IllegalArgumentException("J1939 write requires value/data hex or numeric field");
    }

    static int extractSa(DataRecord value, int fallback) {
        if (value == null || value.rowCount() == 0) {
            return fallback;
        }
        Map<String, Object> row = value.firstRow();
        Object sa = row.get("sa");
        if (sa == null) {
            sa = row.get("sourceAddress");
        }
        if (sa == null) {
            return fallback;
        }
        return parseIntFlexible(String.valueOf(sa));
    }

    static String toDataHex(Object candidate) {
        String text = String.valueOf(candidate).trim();
        if (text.isEmpty()) {
            return "";
        }
        if (text.matches("(?i)0x[0-9A-F]+") || text.matches("(?i)[0-9A-F]*[A-F][0-9A-F]*")) {
            return normalizeHex(text);
        }
        if (text.matches("-?\\d+")) {
            long number = Long.parseLong(text);
            if (number < 0) {
                throw new IllegalArgumentException("Negative numeric J1939 payload unsupported: " + text);
            }
            String hex = Long.toHexString(number).toUpperCase(Locale.ROOT);
            if ((hex.length() % 2) != 0) {
                hex = "0" + hex;
            }
            return hex;
        }
        return normalizeHex(text);
    }

    static int parseIntFlexible(String raw) {
        String text = raw.trim();
        if (text.regionMatches(true, 0, "0x", 0, 2)) {
            return Integer.parseInt(text.substring(2), 16);
        }
        if (text.matches("(?i)[0-9A-F]*[A-F][0-9A-F]*")) {
            return Integer.parseInt(text, 16);
        }
        return Integer.parseInt(text, 10);
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
                    throw new IOException("EOF reading J1939 gateway line");
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

    record Frame(int pgn, int sa, String dataHex) {
    }
}
