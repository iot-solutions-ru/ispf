package com.ispf.driver.codesys;

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
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * CODESYS Gateway driver — lab text dialect over TCP (default port {@code 1217}).
 * <p>
 * Honesty boundary: this is an ISPF CODESYS-lab gateway dialect, not the official
 * CODESYS Network Protocol, not PLCHandler binary, and not CODESYS Automation Server proprietary
 * libraries. Line commands:
 * <pre>
 *   GET &lt;symbol&gt;{@code \\n}           →  OK &lt;symbol&gt;=&lt;value&gt;{@code \\n}
 *   SET &lt;symbol&gt; &lt;value&gt;{@code \\n}   →  OK &lt;symbol&gt;=&lt;value&gt;{@code \\n}
 * </pre>
 * Point mappings are IEC symbol paths such as {@code Application.GVL.MotorSpeed}.
 * <p>
 * Clean-room ISPF code, Apache-2.0 — JDK sockets only; no GPL / proprietary SDKs.
 */
public class CodesysDeviceDriver implements DeviceDriver {

    private static final Pattern OK_VALUE = Pattern.compile(
            "^OK\\s+(.+?)=(.*)$",
            Pattern.CASE_INSENSITIVE);

    private static final DataSchema VALUE_SCHEMA = DataSchema.builder("codesysSymbol")
            .field("value", FieldType.STRING)
            .field("symbol", FieldType.STRING)
            .build();

    private static final DriverMetadata METADATA = new DriverMetadata(
            "codesys",
            "CODESYS Gateway Driver",
            "0.1.0",
            "CODESYS-lab text GET/SET gateway on TCP 1217 — not official CODESYS Network Protocol / PLCHandler",
            "ISPF",
            Map.of(
                    "host", "127.0.0.1",
                    "port", "1217",
                    "timeoutMs", "3000",
                    "pollIntervalMs", "5000"
            ),
            null,
            Set.of("read", "write")
    );

    private DriverObject driverObject;
    private String host = "127.0.0.1";
    private int port = 1217;
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
                    "CODESYS-lab gateway connected to " + host + ":" + port
                            + " (text GET/SET dialect — not official CODESYS Network Protocol)");
        } catch (IOException e) {
            closeSocket();
            throw new DriverException("CODESYS-lab gateway connect failed for " + host + ":" + port, e);
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
            String symbol = normalizeSymbol(
                    entry.getValue() == null || entry.getValue().isBlank() ? pointId : entry.getValue());
            points.put(pointId, symbol);
            String value = getSymbol(symbol);
            driverObject.updateVariable(pointId, DataRecord.single(VALUE_SCHEMA, Map.of(
                    "value", value,
                    "symbol", symbol
            )));
        }
    }

    @Override
    public void writePoint(String pointId, DataRecord value) throws DriverException {
        if (!isConnected()) {
            throw new DriverException("Not connected");
        }
        String symbol = points.getOrDefault(pointId, normalizeSymbol(pointId));
        String payload = extractValue(value);
        String written = setSymbol(symbol, payload);
        driverObject.updateVariable(pointId, DataRecord.single(VALUE_SCHEMA, Map.of(
                "value", written,
                "symbol", symbol
        )));
    }

    private String getSymbol(String symbol) throws DriverException {
        String response = transact("GET " + symbol);
        return parseOkValue(response, symbol);
    }

    private String setSymbol(String symbol, String payload) throws DriverException {
        String response = transact("SET " + symbol + " " + payload);
        return parseOkValue(response, symbol);
    }

    private synchronized String transact(String command) throws DriverException {
        try {
            writeLine(socket.getOutputStream(), command);
            return readLine(socket.getInputStream());
        } catch (IOException e) {
            throw new DriverException(
                    "CODESYS-lab gateway I/O failed for " + host + ":" + port + " (" + command + ")", e);
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

    static String normalizeSymbol(String mapping) {
        String symbol = mapping == null ? "" : mapping.trim();
        if (symbol.isEmpty()) {
            throw new IllegalArgumentException("Blank CODESYS symbol mapping");
        }
        // Allow accidental "symbol Application.GVL.X" prefixes from copy/paste.
        if (symbol.regionMatches(true, 0, "symbol ", 0, 7)) {
            symbol = symbol.substring(7).trim();
        }
        if (!symbol.matches("[A-Za-z_][A-Za-z0-9_]*(\\.[A-Za-z_][A-Za-z0-9_]*)*")) {
            throw new IllegalArgumentException(
                    "CODESYS symbol must look like Application.GVL.MotorSpeed: " + mapping);
        }
        return symbol;
    }

    static String parseOkValue(String response, String expectedSymbol) {
        if (response == null || response.isBlank()) {
            throw new IllegalArgumentException("Empty CODESYS-lab response");
        }
        String trimmed = response.trim();
        if (trimmed.regionMatches(true, 0, "ERR", 0, 3)) {
            throw new IllegalArgumentException("CODESYS-lab error: " + trimmed);
        }
        Matcher matcher = OK_VALUE.matcher(trimmed);
        if (!matcher.matches()) {
            throw new IllegalArgumentException("Invalid CODESYS-lab response: " + trimmed);
        }
        String symbol = matcher.group(1).trim();
        String value = matcher.group(2);
        if (!symbol.equalsIgnoreCase(expectedSymbol)) {
            throw new IllegalArgumentException(
                    "CODESYS-lab symbol mismatch: expected " + expectedSymbol + " got " + symbol);
        }
        return value;
    }

    static void writeLine(OutputStream out, String line) throws IOException {
        out.write((line + "\n").getBytes(StandardCharsets.UTF_8));
        out.flush();
    }

    static String readLine(InputStream in) throws IOException {
        ByteArrayOutputStream buf = new ByteArrayOutputStream();
        while (true) {
            int ch = in.read();
            if (ch < 0) {
                if (buf.size() == 0) {
                    throw new IOException("EOF reading CODESYS-lab gateway line");
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
        return buf.toString(StandardCharsets.UTF_8);
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
