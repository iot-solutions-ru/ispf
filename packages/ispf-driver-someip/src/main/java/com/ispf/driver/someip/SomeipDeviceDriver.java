package com.ispf.driver.someip;

import com.ispf.core.model.DataRecord;
import com.ispf.core.model.DataSchema;
import com.ispf.core.model.FieldType;
import com.ispf.driver.DeviceDriver;
import com.ispf.driver.DriverException;
import com.ispf.driver.DriverMetadata;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * AUTOSAR SOME/IP driver — lab subset over UDP or TCP (default port {@code 30490}).
 * <p>
 * Honesty boundary: this is an ISPF SOME/IP-lab codec (header + payload request/response),
 * not full Service Discovery, not secure on-wire AUTOSAR, and not a vendor AUTOSAR stack.
 * Header layout (16 bytes):
 * <pre>
 *   service(2) method(2) length(4) client(2) session(2)
 *   protocolVer(1) interfaceVer(1) messageType(1) returnCode(1) + payload
 * </pre>
 * Point mappings accept {@code service:method} such as {@code 0x1234:0x0001}.
 * Reads issue REQUEST (0x00) and expect RESPONSE (0x80); writes use REQUEST_NO_RETURN (0x01)
 * or a REQUEST/RESPONSE set depending on {@code writeMode}.
 * <p>
 * Clean-room ISPF code, Apache-2.0 — JDK sockets only.
 */
public class SomeipDeviceDriver implements DeviceDriver {

    static final byte MSG_REQUEST = 0x00;
    static final byte MSG_REQUEST_NO_RETURN = 0x01;
    static final byte MSG_RESPONSE = (byte) 0x80;
    static final byte PROTOCOL_VERSION = 0x01;
    static final byte INTERFACE_VERSION = 0x01;
    static final byte E_OK = 0x00;

    private static final Pattern SERVICE_METHOD = Pattern.compile(
            "^(?:0x)?([0-9A-Fa-f]+)\\s*[:.]\\s*(?:0x)?([0-9A-Fa-f]+)$",
            Pattern.CASE_INSENSITIVE);

    private static final DataSchema VALUE_SCHEMA = DataSchema.builder("someipValue")
            .field("value", FieldType.STRING)
            .field("data", FieldType.STRING)
            .field("service", FieldType.STRING)
            .field("method", FieldType.STRING)
            .field("raw", FieldType.STRING)
            .build();

    private static final DriverMetadata METADATA = new DriverMetadata(
            "someip",
            "SOME/IP Driver",
            "0.1.0",
            "SOME/IP-lab subset over UDP/TCP (header+payload) — not full SD / secure AUTOSAR stack",
            "ISPF",
            Map.of(
                    "host", "127.0.0.1",
                    "port", "30490",
                    "transport", "udp",
                    "clientId", "0x0001",
                    "writeMode", "fireForget",
                    "timeoutMs", "3000",
                    "pollIntervalMs", "5000"
            ),
            null,
            Set.of("read", "write")
    );

    private DriverObject driverObject;
    private String host = "127.0.0.1";
    private int port = 30490;
    private String transport = "udp";
    private int clientId = 0x0001;
    private String writeMode = "fireForget";
    private int timeoutMs = 3000;
    private DatagramSocket udpSocket;
    private Socket tcpSocket;
    private final AtomicInteger sessionId = new AtomicInteger(1);
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
            case "transport" -> transport = value.trim().toLowerCase(Locale.ROOT);
            case "clientId" -> clientId = parseIntFlexible(value.trim());
            case "writeMode" -> writeMode = value.trim();
            case "timeoutMs" -> timeoutMs = Integer.parseInt(value.trim());
            default -> { }
        }
    }

    @Override
    public void connect() throws DriverException {
        try {
            if ("tcp".equals(transport)) {
                Socket next = new Socket();
                next.connect(new InetSocketAddress(host, port), timeoutMs);
                next.setSoTimeout(timeoutMs);
                next.setTcpNoDelay(true);
                tcpSocket = next;
            } else {
                DatagramSocket next = new DatagramSocket();
                next.setSoTimeout(timeoutMs);
                next.connect(new InetSocketAddress(InetAddress.getByName(host), port));
                udpSocket = next;
            }
            connected = true;
            driverObject.log(DriverLogLevel.INFO,
                    "SOME/IP-lab connected via " + transport + " to " + host + ":" + port
                            + " (lab subset — not full SD / secure AUTOSAR)");
        } catch (IOException e) {
            closeTransport();
            throw new DriverException("SOME/IP-lab connect failed for " + host + ":" + port, e);
        }
    }

    @Override
    public void disconnect() {
        connected = false;
        points.clear();
        closeTransport();
    }

    @Override
    public boolean isConnected() {
        if (!connected) {
            return false;
        }
        if ("tcp".equals(transport)) {
            return tcpSocket != null && tcpSocket.isConnected() && !tcpSocket.isClosed();
        }
        return udpSocket != null && !udpSocket.isClosed();
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
            ServiceMethod sm = parseServiceMethod(mapping);
            byte[] payload = request(sm.service, sm.method, new byte[0], MSG_REQUEST, true);
            publish(pointId, sm, payload);
        }
    }

    @Override
    public void writePoint(String pointId, DataRecord value) throws DriverException {
        if (!isConnected()) {
            throw new DriverException("Not connected");
        }
        String mapping = points.getOrDefault(pointId, pointId);
        ServiceMethod sm = parseServiceMethod(mapping);
        byte[] payload = extractPayload(value);
        boolean expectResponse = !"fireForget".equalsIgnoreCase(writeMode)
                && !"noReturn".equalsIgnoreCase(writeMode);
        byte messageType = expectResponse ? MSG_REQUEST : MSG_REQUEST_NO_RETURN;
        byte[] responsePayload = request(sm.service, sm.method, payload, messageType, expectResponse);
        publish(pointId, sm, expectResponse ? responsePayload : payload);
    }

    private void publish(String pointId, ServiceMethod sm, byte[] payload) {
        String hex = toHex(payload);
        String text = tryUtf8(payload);
        driverObject.updateVariable(pointId, DataRecord.single(VALUE_SCHEMA, Map.of(
                "value", text != null ? text : hex,
                "data", hex,
                "service", formatId(sm.service),
                "method", formatId(sm.method),
                "raw", hex
        )));
    }

    private synchronized byte[] request(
            int service,
            int method,
            byte[] payload,
            byte messageType,
            boolean expectResponse
    ) throws DriverException {
        int session = sessionId.getAndUpdate(v -> v == 0xFFFF ? 1 : v + 1);
        byte[] frame = encodeFrame(service, method, clientId, session, messageType, E_OK, payload);
        try {
            send(frame);
            if (!expectResponse) {
                return payload;
            }
            byte[] response = receive();
            SomeipFrame parsed = decodeFrame(response);
            if (parsed.service != service || parsed.method != method) {
                throw new DriverException("SOME/IP-lab response service/method mismatch");
            }
            if (parsed.messageType != MSG_RESPONSE) {
                throw new DriverException("SOME/IP-lab expected RESPONSE, got 0x"
                        + Integer.toHexString(parsed.messageType & 0xFF));
            }
            if (parsed.returnCode != E_OK) {
                throw new DriverException("SOME/IP-lab return code 0x"
                        + Integer.toHexString(parsed.returnCode & 0xFF));
            }
            return parsed.payload;
        } catch (IOException e) {
            throw new DriverException(
                    "SOME/IP-lab I/O failed for " + formatId(service) + ":" + formatId(method), e);
        }
    }

    private void send(byte[] frame) throws IOException {
        if ("tcp".equals(transport)) {
            OutputStream out = tcpSocket.getOutputStream();
            out.write(frame);
            out.flush();
        } else {
            DatagramPacket packet = new DatagramPacket(frame, frame.length);
            udpSocket.send(packet);
        }
    }

    private byte[] receive() throws IOException {
        if ("tcp".equals(transport)) {
            return readTcpFrame(tcpSocket.getInputStream());
        }
        byte[] buf = new byte[65535];
        DatagramPacket packet = new DatagramPacket(buf, buf.length);
        udpSocket.receive(packet);
        return Arrays.copyOf(packet.getData(), packet.getLength());
    }

    private void closeTransport() {
        if (udpSocket != null) {
            udpSocket.close();
            udpSocket = null;
        }
        if (tcpSocket != null) {
            try {
                tcpSocket.close();
            } catch (IOException ignored) {
                // best-effort
            }
            tcpSocket = null;
        }
    }

    static ServiceMethod parseServiceMethod(String mapping) {
        if (mapping == null || mapping.isBlank()) {
            throw new IllegalArgumentException("Blank SOME/IP mapping");
        }
        Matcher matcher = SERVICE_METHOD.matcher(mapping.trim());
        if (!matcher.matches()) {
            throw new IllegalArgumentException(
                    "Unsupported SOME/IP mapping (expected 0x1234:0x0001): " + mapping);
        }
        int service = parseIntFlexible(matcher.group(1));
        int method = parseIntFlexible(matcher.group(2));
        if (service < 0 || service > 0xFFFF || method < 0 || method > 0xFFFF) {
            throw new IllegalArgumentException("SOME/IP service/method out of range: " + mapping);
        }
        return new ServiceMethod(service, method);
    }

    static byte[] encodeFrame(
            int service,
            int method,
            int clientId,
            int sessionId,
            byte messageType,
            byte returnCode,
            byte[] payload
    ) {
        int length = 8 + payload.length; // from request/client onward
        ByteBuffer buf = ByteBuffer.allocate(16 + payload.length);
        buf.putShort((short) service);
        buf.putShort((short) method);
        buf.putInt(length);
        buf.putShort((short) clientId);
        buf.putShort((short) sessionId);
        buf.put(PROTOCOL_VERSION);
        buf.put(INTERFACE_VERSION);
        buf.put(messageType);
        buf.put(returnCode);
        buf.put(payload);
        return buf.array();
    }

    static SomeipFrame decodeFrame(byte[] frame) {
        if (frame.length < 16) {
            throw new IllegalArgumentException("SOME/IP frame too short: " + frame.length);
        }
        ByteBuffer buf = ByteBuffer.wrap(frame);
        int service = buf.getShort() & 0xFFFF;
        int method = buf.getShort() & 0xFFFF;
        int length = buf.getInt();
        int client = buf.getShort() & 0xFFFF;
        int session = buf.getShort() & 0xFFFF;
        byte protocol = buf.get();
        byte iface = buf.get();
        byte messageType = buf.get();
        byte returnCode = buf.get();
        if (protocol != PROTOCOL_VERSION) {
            throw new IllegalArgumentException("Unexpected SOME/IP protocol version: " + protocol);
        }
        int payloadLen = length - 8;
        if (payloadLen < 0 || 16 + payloadLen > frame.length) {
            throw new IllegalArgumentException("Invalid SOME/IP length: " + length);
        }
        byte[] payload = Arrays.copyOfRange(frame, 16, 16 + payloadLen);
        return new SomeipFrame(service, method, client, session, iface, messageType, returnCode, payload);
    }

    static byte[] readTcpFrame(InputStream in) throws IOException {
        byte[] header = readFully(in, 16);
        int length = ByteBuffer.wrap(header, 4, 4).getInt();
        int payloadLen = length - 8;
        if (payloadLen < 0 || payloadLen > 65536) {
            throw new IOException("Invalid SOME/IP TCP length: " + length);
        }
        if (payloadLen == 0) {
            return header;
        }
        byte[] payload = readFully(in, payloadLen);
        byte[] frame = new byte[16 + payloadLen];
        System.arraycopy(header, 0, frame, 0, 16);
        System.arraycopy(payload, 0, frame, 16, payloadLen);
        return frame;
    }

    static byte[] readFully(InputStream in, int length) throws IOException {
        byte[] buf = new byte[length];
        int offset = 0;
        while (offset < length) {
            int n = in.read(buf, offset, length - offset);
            if (n < 0) {
                throw new IOException("EOF reading SOME/IP TCP frame");
            }
            offset += n;
        }
        return buf;
    }

    static byte[] extractPayload(DataRecord value) {
        if (value == null || value.rowCount() == 0) {
            return new byte[0];
        }
        Map<String, Object> row = value.firstRow();
        for (String key : List.of("data", "value", "payload", "raw", "hex")) {
            Object candidate = row.get(key);
            if (candidate != null) {
                return toBytes(String.valueOf(candidate).trim());
            }
        }
        if (row.size() == 1) {
            return toBytes(String.valueOf(row.values().iterator().next()).trim());
        }
        throw new IllegalArgumentException("SOME/IP write requires value/data field");
    }

    static byte[] toBytes(String text) {
        if (text.isEmpty()) {
            return new byte[0];
        }
        if (text.matches("(?i)0x([0-9A-F]{2})+") || text.matches("(?i)([0-9A-F]{2})+")) {
            String hex = text.regionMatches(true, 0, "0x", 0, 2) ? text.substring(2) : text;
            return fromHex(hex);
        }
        return text.getBytes(StandardCharsets.UTF_8);
    }

    static byte[] fromHex(String hex) {
        String clean = hex.replace(" ", "").toUpperCase(Locale.ROOT);
        if ((clean.length() % 2) != 0 || !clean.matches("[0-9A-F]*")) {
            throw new IllegalArgumentException("Invalid hex payload: " + hex);
        }
        byte[] out = new byte[clean.length() / 2];
        for (int i = 0; i < out.length; i++) {
            out[i] = (byte) Integer.parseInt(clean.substring(i * 2, i * 2 + 2), 16);
        }
        return out;
    }

    static String toHex(byte[] data) {
        StringBuilder sb = new StringBuilder(data.length * 2);
        for (byte b : data) {
            sb.append(String.format(Locale.ROOT, "%02X", b & 0xFF));
        }
        return sb.toString();
    }

    static String tryUtf8(byte[] data) {
        if (data.length == 0) {
            return "";
        }
        String text = new String(data, StandardCharsets.UTF_8);
        for (int i = 0; i < text.length(); i++) {
            char ch = text.charAt(i);
            if (ch < 0x20 || ch == 0x7F) {
                return null;
            }
        }
        return text;
    }

    static String formatId(int id) {
        return String.format(Locale.ROOT, "0x%04X", id);
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

    record ServiceMethod(int service, int method) {
    }

    record SomeipFrame(
            int service,
            int method,
            int clientId,
            int sessionId,
            byte interfaceVersion,
            byte messageType,
            byte returnCode,
            byte[] payload
    ) {
    }
}
