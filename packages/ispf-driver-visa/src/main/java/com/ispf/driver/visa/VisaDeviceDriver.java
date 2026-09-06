package com.ispf.driver.visa;

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
 * VISA-style TCP SOCKET resource driver — clean-room SCPI-over-TCP facade.
 * <p>
 * Supports only resource strings of the form
 * {@code TCPIP[board]::host::port::SOCKET} (for example {@code TCPIP0::127.0.0.1::5025::SOCKET}).
 * This is <strong>not</strong> NI-VISA, IVI-VISA, or any proprietary VISA shared library: there is
 * no GPIB/USB/PXI/{@code INSTR} support, no VISA attribute model, and no claim of VISA API
 * compatibility beyond the familiar SOCKET resource-string shape used to open a raw TCP port for
 * IEEE 488.2-style ASCII (SCPI) traffic.
 * <p>
 * Point mapping is the SCPI query or set command (same semantics as the ISPF SCPI driver).
 * Opens one TCP session on {@link #connect()} and reuses it until {@link #disconnect()}.
 * Clean-room ISPF code, Apache-2.0 — JDK sockets only.
 */
public class VisaDeviceDriver implements DeviceDriver {

    private static final Pattern SOCKET_RESOURCE = Pattern.compile(
            "^TCPIP(\\d*)::([^:]+)::(\\d+)::SOCKET$",
            Pattern.CASE_INSENSITIVE
    );

    private static final DataSchema VALUE_SCHEMA = DataSchema.builder("visaSocketValue")
            .field("value", FieldType.STRING)
            .field("command", FieldType.STRING)
            .field("resource", FieldType.STRING)
            .build();

    private static final DriverMetadata METADATA = new DriverMetadata(
            "visa",
            "VISA-style TCP SOCKET Driver",
            "0.1.0",
            "SOCKET-only VISA-style resource strings (TCPIP::host::port::SOCKET) for SCPI-over-TCP — not NI-VISA",
            "ISPF",
            Map.of(
                    "resource", "TCPIP0::127.0.0.1::5025::SOCKET",
                    "host", "127.0.0.1",
                    "port", "5025",
                    "timeoutMs", "3000",
                    "pollIntervalMs", "5000"
            ),
            null,
            Set.of("read", "write")
    );

    private DriverObject driverObject;
    private String resource = "TCPIP0::127.0.0.1::5025::SOCKET";
    private String host = "127.0.0.1";
    private int port = 5025;
    private int timeoutMs = 3000;
    private boolean resourceConfigured;
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
        this.resourceConfigured = false;
        driverObject.configuration().forEach(this::applyConfig);
        if (!resourceConfigured) {
            this.resource = "TCPIP0::" + host + "::" + port + "::SOCKET";
        }
    }

    private void applyConfig(String key, String value) {
        if (value == null || value.isBlank()) {
            return;
        }
        switch (key) {
            case "resource" -> {
                resource = value.trim();
                resourceConfigured = true;
            }
            case "host" -> host = value.trim();
            case "port" -> port = Integer.parseInt(value.trim());
            case "timeoutMs" -> timeoutMs = Integer.parseInt(value.trim());
            default -> { }
        }
    }

    @Override
    public void connect() throws DriverException {
        applySocketResource(resource);
        try {
            Socket next = new Socket();
            next.connect(new InetSocketAddress(host, port), timeoutMs);
            next.setSoTimeout(timeoutMs);
            next.setTcpNoDelay(true);
            socket = next;
            connected = true;
            driverObject.log(DriverLogLevel.INFO,
                    "VISA-style SOCKET connected " + resource + " (" + host + ":" + port + ")");
        } catch (IOException e) {
            closeSocket();
            throw new DriverException("SOCKET connect failed for " + resource, e);
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
            String command = entry.getValue() == null || entry.getValue().isBlank()
                    ? pointId
                    : entry.getValue().trim();
            points.put(pointId, command);
            String response = isQuery(command) ? query(command) : "";
            driverObject.updateVariable(pointId, DataRecord.single(VALUE_SCHEMA, Map.of(
                    "value", response == null ? "" : response,
                    "command", command,
                    "resource", resource
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
        if (isQuery(command)) {
            String response = query(command);
            driverObject.updateVariable(pointId, DataRecord.single(VALUE_SCHEMA, Map.of(
                    "value", response == null ? "" : response,
                    "command", command,
                    "resource", resource
            )));
        } else {
            send(command);
            driverObject.updateVariable(pointId, DataRecord.single(VALUE_SCHEMA, Map.of(
                    "value", payload,
                    "command", command,
                    "resource", resource
            )));
        }
    }

    /**
     * Parses a SOCKET-only VISA-style resource string into host/port.
     * Rejects {@code INSTR}, GPIB, USB, and other non-SOCKET forms with a clear error.
     */
    static SocketEndpoint parseSocketResource(String resource) throws DriverException {
        if (resource == null || resource.isBlank()) {
            throw new DriverException("VISA-style resource must not be blank");
        }
        String trimmed = resource.trim();
        Matcher matcher = SOCKET_RESOURCE.matcher(trimmed);
        if (!matcher.matches()) {
            throw new DriverException(
                    "Unsupported VISA-style resource '" + trimmed
                            + "'. Only TCPIP[board]::host::port::SOCKET is implemented "
                            + "(not NI-VISA; no INSTR/GPIB/USB/PXI)."
            );
        }
        return new SocketEndpoint(matcher.group(2), Integer.parseInt(matcher.group(3)), trimmed);
    }

    private void applySocketResource(String resourceString) throws DriverException {
        SocketEndpoint endpoint = parseSocketResource(resourceString);
        this.resource = endpoint.resource();
        this.host = endpoint.host();
        this.port = endpoint.port();
    }

    static String buildWriteCommand(String mapping, String payload) {
        String map = mapping == null ? "" : mapping.trim();
        String body = payload == null ? "" : payload;
        if (map.contains("{value}")) {
            return map.replace("{value}", body);
        }
        if (map.isBlank()) {
            return body;
        }
        if (isQuery(map)) {
            String base = map.substring(0, map.length() - 1).trim();
            return base.isBlank() ? body : base + " " + body;
        }
        return map + " " + body;
    }

    static boolean isQuery(String command) {
        return command != null && command.trim().endsWith("?");
    }

    private synchronized String query(String command) throws DriverException {
        try {
            writeLine(socket.getOutputStream(), command);
            return readLine(socket.getInputStream());
        } catch (IOException e) {
            throw new DriverException(
                    "SOCKET SCPI query failed for " + resource + " (" + command + ")", e);
        }
    }

    private synchronized void send(String command) throws DriverException {
        try {
            writeLine(socket.getOutputStream(), command);
        } catch (IOException e) {
            throw new DriverException(
                    "SOCKET SCPI write failed for " + resource + " (" + command + ")", e);
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

    static void writeLine(OutputStream out, String command) throws IOException {
        out.write((command.trim() + "\n").getBytes(StandardCharsets.US_ASCII));
        out.flush();
    }

    static String readLine(InputStream in) throws IOException {
        ByteArrayOutputStream line = new ByteArrayOutputStream();
        while (true) {
            int ch = in.read();
            if (ch < 0) {
                if (line.size() == 0) {
                    throw new IOException("EOF reading SOCKET SCPI response");
                }
                break;
            }
            if (ch == '\n') {
                break;
            }
            if (ch != '\r') {
                line.write(ch);
            }
        }
        return line.toString(StandardCharsets.US_ASCII).trim();
    }

    private static String extractValue(DataRecord value) {
        if (value == null || value.rowCount() == 0) {
            return "";
        }
        Map<String, Object> row = value.firstRow();
        for (String key : List.of("value", "payload", "data", "text", "raw", "command")) {
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

    record SocketEndpoint(String host, int port, String resource) {
    }
}
