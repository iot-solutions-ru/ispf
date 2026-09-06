package com.ispf.driver.uds;

import com.ispf.core.model.DataRecord;
import com.ispf.core.model.DataSchema;
import com.ispf.core.model.FieldType;
import com.ispf.driver.DeviceDriver;
import com.ispf.driver.DriverException;
import com.ispf.driver.DriverMetadata;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
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
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * UDS (ISO 14229) driver — DoIP-lab TCP subset (default port {@code 13400}).
 * <p>
 * Honesty boundary: this is an ISPF DoIP/UDS lab codec, not a full ISO 13400 stack,
 * not a complete ISO-TP multi-frame transport, and not Vector/Peak/ETAS tooling.
 * Lab framing is DoIP-like over a single TCP stream:
 * <pre>
 *   [ver][~ver][payloadType u16][length u32][payload…]
 * </pre>
 * On connect the driver performs a lab routing-activation handshake then
 * {@code DiagnosticSessionControl (0x10)}. Point reads use {@code ReadDataByIdentifier (0x22)};
 * writes use {@code WriteDataByIdentifier (0x2E)}.
 * Point mappings accept {@code 0xF190}, {@code DID:F190}, or {@code F190}.
 * <p>
 * Clean-room ISPF code, Apache-2.0 — JDK sockets only.
 */
public class UdsDeviceDriver implements DeviceDriver {

    static final byte DOIP_VERSION = 0x02;
    static final int PAYLOAD_ROUTING_ACTIVATION_REQUEST = 0x0005;
    static final int PAYLOAD_ROUTING_ACTIVATION_RESPONSE = 0x0006;
    static final int PAYLOAD_DIAGNOSTIC_MESSAGE = 0x8001;

    static final int SID_DIAGNOSTIC_SESSION_CONTROL = 0x10;
    static final int SID_READ_DATA_BY_IDENTIFIER = 0x22;
    static final int SID_WRITE_DATA_BY_IDENTIFIER = 0x2E;

    private static final Pattern DID_MAPPING = Pattern.compile(
            "^(?:DID[:\\s-]*)?(?:0x)?([0-9A-Fa-f]+)$",
            Pattern.CASE_INSENSITIVE);

    private static final DataSchema VALUE_SCHEMA = DataSchema.builder("udsDidValue")
            .field("value", FieldType.STRING)
            .field("data", FieldType.STRING)
            .field("did", FieldType.STRING)
            .field("raw", FieldType.STRING)
            .build();

    private static final DriverMetadata METADATA = new DriverMetadata(
            "uds",
            "UDS (ISO 14229) Driver",
            "0.1.0",
            "DoIP/UDS lab subset over TCP (0x10/0x22/0x2E) — not full ISO-TP / ISO 13400 stack",
            "ISPF",
            Map.of(
                    "host", "127.0.0.1",
                    "port", "13400",
                    "sourceAddress", "0x0E00",
                    "targetAddress", "0x0001",
                    "sessionType", "0x01",
                    "timeoutMs", "3000",
                    "pollIntervalMs", "5000"
            ),
            null,
            Set.of("read", "write")
    );

    private DriverObject driverObject;
    private String host = "127.0.0.1";
    private int port = 13400;
    private int sourceAddress = 0x0E00;
    private int targetAddress = 0x0001;
    private int sessionType = 0x01;
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
            case "sourceAddress", "sa" -> sourceAddress = parseIntFlexible(value.trim());
            case "targetAddress", "ta" -> targetAddress = parseIntFlexible(value.trim());
            case "sessionType" -> sessionType = parseIntFlexible(value.trim());
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
            activateRouting();
            diagnosticSessionControl(sessionType);
            connected = true;
            driverObject.log(DriverLogLevel.INFO,
                    "UDS DoIP-lab connected to " + host + ":" + port
                            + " (lab subset — not full ISO-TP / ISO 13400)");
        } catch (IOException e) {
            closeSocket();
            throw new DriverException("UDS DoIP-lab connect failed for " + host + ":" + port, e);
        } catch (DriverException e) {
            closeSocket();
            throw e;
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
            int did = parseDidMapping(mapping);
            byte[] data = readDataByIdentifier(did);
            String hex = toHex(data);
            String text = tryUtf8(data);
            driverObject.updateVariable(pointId, DataRecord.single(VALUE_SCHEMA, Map.of(
                    "value", text != null ? text : hex,
                    "data", hex,
                    "did", formatDid(did),
                    "raw", hex
            )));
        }
    }

    @Override
    public void writePoint(String pointId, DataRecord value) throws DriverException {
        if (!isConnected()) {
            throw new DriverException("Not connected");
        }
        String mapping = points.getOrDefault(pointId, pointId);
        int did = parseDidMapping(mapping);
        byte[] payload = extractPayload(value);
        writeDataByIdentifier(did, payload);
        String hex = toHex(payload);
        String text = tryUtf8(payload);
        driverObject.updateVariable(pointId, DataRecord.single(VALUE_SCHEMA, Map.of(
                "value", text != null ? text : hex,
                "data", hex,
                "did", formatDid(did),
                "raw", hex
        )));
    }

    private void activateRouting() throws DriverException, IOException {
        byte[] requestPayload = new byte[11];
        requestPayload[0] = (byte) ((sourceAddress >> 8) & 0xFF);
        requestPayload[1] = (byte) (sourceAddress & 0xFF);
        requestPayload[2] = 0x00; // default activation
        writeDoip(PAYLOAD_ROUTING_ACTIVATION_REQUEST, requestPayload);
        DoipMessage response = readDoip();
        if (response.payloadType != PAYLOAD_ROUTING_ACTIVATION_RESPONSE) {
            throw new DriverException("UDS DoIP-lab expected routing activation response, got 0x"
                    + Integer.toHexString(response.payloadType));
        }
        if (response.payload.length < 5 || (response.payload[4] & 0xFF) != 0x10) {
            throw new DriverException("UDS DoIP-lab routing activation rejected");
        }
    }

    private void diagnosticSessionControl(int type) throws DriverException, IOException {
        byte[] response = transactUds(new byte[]{
                (byte) SID_DIAGNOSTIC_SESSION_CONTROL,
                (byte) (type & 0xFF)
        });
        expectPositive(response, SID_DIAGNOSTIC_SESSION_CONTROL);
    }

    private byte[] readDataByIdentifier(int did) throws DriverException {
        try {
            byte[] response = transactUds(new byte[]{
                    (byte) SID_READ_DATA_BY_IDENTIFIER,
                    (byte) ((did >> 8) & 0xFF),
                    (byte) (did & 0xFF)
            });
            expectPositive(response, SID_READ_DATA_BY_IDENTIFIER);
            if (response.length < 3) {
                throw new DriverException("UDS 0x22 response too short");
            }
            int responseDid = ((response[1] & 0xFF) << 8) | (response[2] & 0xFF);
            if (responseDid != did) {
                throw new DriverException("UDS 0x22 DID mismatch: expected " + did + " got " + responseDid);
            }
            return Arrays.copyOfRange(response, 3, response.length);
        } catch (IOException e) {
            throw new DriverException("UDS ReadDataByIdentifier failed for DID " + formatDid(did), e);
        }
    }

    private void writeDataByIdentifier(int did, byte[] data) throws DriverException {
        try {
            byte[] request = new byte[3 + data.length];
            request[0] = (byte) SID_WRITE_DATA_BY_IDENTIFIER;
            request[1] = (byte) ((did >> 8) & 0xFF);
            request[2] = (byte) (did & 0xFF);
            System.arraycopy(data, 0, request, 3, data.length);
            byte[] response = transactUds(request);
            expectPositive(response, SID_WRITE_DATA_BY_IDENTIFIER);
        } catch (IOException e) {
            throw new DriverException("UDS WriteDataByIdentifier failed for DID " + formatDid(did), e);
        }
    }

    private synchronized byte[] transactUds(byte[] udsRequest) throws IOException, DriverException {
        byte[] payload = new byte[4 + udsRequest.length];
        payload[0] = (byte) ((sourceAddress >> 8) & 0xFF);
        payload[1] = (byte) (sourceAddress & 0xFF);
        payload[2] = (byte) ((targetAddress >> 8) & 0xFF);
        payload[3] = (byte) (targetAddress & 0xFF);
        System.arraycopy(udsRequest, 0, payload, 4, udsRequest.length);
        writeDoip(PAYLOAD_DIAGNOSTIC_MESSAGE, payload);
        DoipMessage response = readDoip();
        if (response.payloadType != PAYLOAD_DIAGNOSTIC_MESSAGE) {
            throw new DriverException("UDS DoIP-lab expected diagnostic message, got 0x"
                    + Integer.toHexString(response.payloadType));
        }
        if (response.payload.length < 5) {
            throw new DriverException("UDS DoIP-lab diagnostic payload too short");
        }
        return Arrays.copyOfRange(response.payload, 4, response.payload.length);
    }

    private synchronized void writeDoip(int payloadType, byte[] payload) throws IOException {
        writeDoipFrame(socket.getOutputStream(), payloadType, payload);
    }

    private synchronized DoipMessage readDoip() throws IOException {
        return readDoipFrame(socket.getInputStream());
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

    static void expectPositive(byte[] response, int requestSid) throws DriverException {
        if (response == null || response.length == 0) {
            throw new DriverException("Empty UDS response");
        }
        int sid = response[0] & 0xFF;
        if (sid == 0x7F) {
            int nrc = response.length > 2 ? (response[2] & 0xFF) : -1;
            throw new DriverException("UDS negative response NRC=0x" + Integer.toHexString(nrc));
        }
        if (sid != ((requestSid + 0x40) & 0xFF)) {
            throw new DriverException("UDS unexpected SID 0x" + Integer.toHexString(sid)
                    + " for request 0x" + Integer.toHexString(requestSid));
        }
    }

    static int parseDidMapping(String mapping) {
        if (mapping == null || mapping.isBlank()) {
            throw new IllegalArgumentException("Blank UDS DID mapping");
        }
        Matcher matcher = DID_MAPPING.matcher(mapping.trim());
        if (!matcher.matches()) {
            throw new IllegalArgumentException("Unsupported UDS mapping (expected 0xF190 or DID:F190): " + mapping);
        }
        int did = Integer.parseInt(matcher.group(1), 16);
        if (did < 0 || did > 0xFFFF) {
            throw new IllegalArgumentException("UDS DID out of range: " + did);
        }
        return did;
    }

    static String formatDid(int did) {
        return String.format(Locale.ROOT, "0x%04X", did);
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
        throw new IllegalArgumentException("UDS write requires value/data field");
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

    static void writeDoipFrame(OutputStream out, int payloadType, byte[] payload) throws IOException {
        ByteBuffer header = ByteBuffer.allocate(8 + payload.length);
        header.put(DOIP_VERSION);
        header.put((byte) (~DOIP_VERSION));
        header.putShort((short) payloadType);
        header.putInt(payload.length);
        header.put(payload);
        out.write(header.array());
        out.flush();
    }

    static DoipMessage readDoipFrame(InputStream in) throws IOException {
        byte[] header = readFully(in, 8);
        int version = header[0] & 0xFF;
        int inverse = header[1] & 0xFF;
        if (version != (DOIP_VERSION & 0xFF) || inverse != ((~DOIP_VERSION) & 0xFF)) {
            throw new IOException("Invalid DoIP-lab version header");
        }
        int payloadType = ((header[2] & 0xFF) << 8) | (header[3] & 0xFF);
        int length = ByteBuffer.wrap(header, 4, 4).getInt();
        if (length < 0 || length > 65536) {
            throw new IOException("Invalid DoIP-lab payload length: " + length);
        }
        byte[] payload = readFully(in, length);
        return new DoipMessage(payloadType, payload);
    }

    static byte[] readFully(InputStream in, int length) throws IOException {
        byte[] buf = new byte[length];
        int offset = 0;
        while (offset < length) {
            int n = in.read(buf, offset, length - offset);
            if (n < 0) {
                throw new IOException("EOF reading DoIP-lab frame");
            }
            offset += n;
        }
        return buf;
    }

    record DoipMessage(int payloadType, byte[] payload) {
    }
}
