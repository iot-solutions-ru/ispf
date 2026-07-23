package com.ispf.driver.ethernetip;

import com.ispf.core.model.DataRecord;
import com.ispf.core.model.DataSchema;
import com.ispf.core.model.FieldType;
import com.ispf.driver.DeviceDriver;
import com.ispf.driver.DriverException;
import com.ispf.driver.DriverMetadata;

import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.EOFException;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * EtherNet/IP driver — CIP client over the EtherNet/IP encapsulation protocol.
 *
 * <p>{@link #connect()} opens TCP (default port 44818) and performs
 * {@code RegisterSession}. Polls issue UCMM (unconnected) messages via
 * {@code SendRRData}: CIP Read Tag service (0x4C) with the tag path encoded as
 * ANSI extended symbolic segments (0x91, dot-separated name segments). Atomic
 * types BOOL, SINT, INT, DINT and REAL are decoded little-endian.
 * {@link #writePoint} uses CIP Write Tag service (0x4D) with the CIP type
 * learned from the last read of that tag, falling back to Java value type
 * inference (Boolean→BOOL, Integer/Long→DINT, Float/Double→REAL).
 *
 * <p>A non-zero CIP general status marks the point quality BAD instead of
 * returning a fabricated value. Limitations: UCMM only (no connected Class-3
 * messaging), element count 1 (no arrays, structures or UDTs, no
 * fragmentation), one UCMM exchange per point per poll, no
 * ListIdentity/ListServices discovery.
 */
public class EthernetIpDeviceDriver implements DeviceDriver {

    private static final int ENCAP_REGISTER_SESSION = 0x0065;
    private static final int ENCAP_SEND_RR_DATA = 0x006F;
    private static final int CPF_NULL_ADDRESS = 0x0000;
    private static final int CPF_UNCONNECTED_DATA = 0x00B2;
    private static final int CIP_READ_TAG = 0x4C;
    private static final int CIP_WRITE_TAG = 0x4D;

    private static final int CIP_BOOL = 0xC1;
    private static final int CIP_SINT = 0xC2;
    private static final int CIP_INT = 0xC3;
    private static final int CIP_DINT = 0xC4;
    private static final int CIP_REAL = 0xCA;

    private static final int DEFAULT_PORT = 44818;

    private static final DriverMetadata METADATA = new DriverMetadata(
            "ethernet-ip",
            "EtherNet/IP Driver",
            "0.2.0",
            "CIP/EtherNet/IP client: UCMM Read/Write Tag for atomic types"
                    + " (BOOL/SINT/INT/DINT/REAL) over EtherNet/IP encapsulation",
            "ISPF",
            Map.of(
                    "host", "127.0.0.1",
                    "port", "44818",
                    "timeoutMs", "5000",
                    "pollIntervalMs", "5000"
            )
    );

    private static final DataSchema STATUS_SCHEMA = DataSchema.builder("ethernetIpStatus")
            .field("connected", FieldType.BOOLEAN)
            .field("sessionHandle", FieldType.LONG)
            .field("tagPath", FieldType.STRING)
            .field("value", FieldType.STRING)
            .field("quality", FieldType.STRING)
            .build();

    private DriverObject driverObject;
    private Socket socket;
    private String host = "127.0.0.1";
    private int port = DEFAULT_PORT;
    private int timeoutMs = 5000;
    private long sessionHandle;
    private final Map<String, EthernetIpPoint> points = new ConcurrentHashMap<>();
    private final Map<String, Integer> tagTypes = new ConcurrentHashMap<>();
    private volatile boolean connected;

    @Override
    public DriverMetadata metadata() {
        return METADATA;
    }

    @Override
    public void initialize(DriverObject driverObject) {
        this.driverObject = driverObject;
        driverObject.configuration().forEach(this::applyConfig);
        readConfig("host", value -> host = value);
        readConfig("port", value -> port = Integer.parseInt(value));
        readConfig("timeoutMs", value -> timeoutMs = Integer.parseInt(value));
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
            socket = new Socket();
            socket.connect(new InetSocketAddress(host, port), timeoutMs);
            socket.setSoTimeout(timeoutMs);
            sessionHandle = registerSession();
            connected = true;
            driverObject.log(DriverLogLevel.INFO,
                    "EtherNet/IP session registered at " + host + ":" + port + " handle=" + sessionHandle);
        } catch (Exception e) {
            connected = false;
            closeSocket();
            throw new DriverException("EtherNet/IP connect failed", e);
        }
    }

    @Override
    public void disconnect() {
        connected = false;
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
            EthernetIpPoint point = EthernetIpPoint.parse(entry.getValue());
            points.put(entry.getKey(), point);
            driverObject.updateVariable(entry.getKey(), readPoint(entry.getKey(), point));
        }
    }

    @Override
    public void writePoint(String pointId, DataRecord value) throws DriverException {
        if (!isConnected()) {
            throw new DriverException("Not connected");
        }
        EthernetIpPoint point = points.get(pointId);
        if (point == null) {
            throw new DriverException("EtherNet/IP point '" + pointId
                    + "' is not mapped; include it in a poll before writing");
        }
        Object raw = value.firstRow().get("value");
        if (raw == null) {
            raw = value.firstRow().get("raw");
        }
        if (raw == null) {
            throw new DriverException("EtherNet/IP write requires a value");
        }
        int type = resolveWriteType(pointId, raw);
        try {
            byte[] request = buildCipRequest(CIP_WRITE_TAG, point.tagPath(), encodeWriteData(type, raw));
            int status = ucmmExchange(request)[1][0] & 0xFF;
            if (status != 0) {
                throw new DriverException("CIP Write Tag failed: generalStatus=0x"
                        + Integer.toHexString(status));
            }
            driverObject.updateVariable(pointId, DataRecord.single(STATUS_SCHEMA, Map.of(
                    "connected", true,
                    "sessionHandle", sessionHandle,
                    "tagPath", point.tagPath(),
                    "value", raw.toString(),
                    "quality", "GOOD"
            )));
        } catch (IOException e) {
            markDisconnected();
            throw new DriverException("EtherNet/IP write failed", e);
        }
    }

    private DataRecord readPoint(String pointId, EthernetIpPoint point) {
        String value = "";
        String quality;
        try {
            byte[] request = buildCipRequest(CIP_READ_TAG, point.tagPath(), new byte[]{1, 0});
            byte[][] cip = ucmmExchange(request);
            int status = cip[1][0] & 0xFF;
            if (status == 0) {
                ByteBuffer data = ByteBuffer.wrap(cip[2]).order(ByteOrder.LITTLE_ENDIAN);
                int type = Short.toUnsignedInt(data.getShort());
                Object decoded = decodeValue(type, data);
                tagTypes.put(pointId, type);
                value = String.valueOf(decoded);
                quality = "GOOD";
            } else {
                quality = "BAD:0x" + Integer.toHexString(status);
            }
        } catch (IOException e) {
            markDisconnected();
            quality = "NOT_AVAILABLE";
        } catch (DriverException e) {
            quality = "NOT_AVAILABLE";
        }
        return DataRecord.single(STATUS_SCHEMA, Map.of(
                "connected", isConnected(),
                "sessionHandle", sessionHandle,
                "tagPath", point.tagPath(),
                "value", value,
                "quality", quality
        ));
    }

    /**
     * Sends one UCMM request and returns the parsed CIP reply as
     * {@code {replyServiceByte, generalStatusByte, dataBytes}}.
     */
    private byte[][] ucmmExchange(byte[] cipRequest) throws IOException {
        byte[] payload = buildSendRrDataPayload(cipRequest);
        byte[] responsePayload = encapExchange(ENCAP_SEND_RR_DATA, payload);
        byte[] cipData = extractUnconnectedData(responsePayload);
        if (cipData.length < 4) {
            throw new IOException("Short CIP response: " + cipData.length + " bytes");
        }
        int additionalWords = cipData[3] & 0xFF;
        int dataOffset = 4 + additionalWords * 2;
        byte[] data = new byte[Math.max(0, cipData.length - dataOffset)];
        System.arraycopy(cipData, dataOffset, data, 0, data.length);
        return new byte[][]{
                new byte[]{cipData[0]},
                new byte[]{cipData[2]},
                data
        };
    }

    private long registerSession() throws IOException {
        ByteBuffer request = ByteBuffer.allocate(28).order(ByteOrder.LITTLE_ENDIAN);
        request.putShort((short) ENCAP_REGISTER_SESSION);
        request.putShort((short) 4);
        request.putInt(0);
        request.putInt(0);
        request.putLong(0L);
        request.putInt(0);
        request.putShort((short) 1);
        request.putShort((short) 0);

        DataOutputStream out = new DataOutputStream(socket.getOutputStream());
        out.write(request.array());
        out.flush();

        byte[] header = readFully(24);
        ByteBuffer buffer = ByteBuffer.wrap(header).order(ByteOrder.LITTLE_ENDIAN);
        int command = Short.toUnsignedInt(buffer.getShort(0));
        int status = buffer.getInt(8);
        if (command != ENCAP_REGISTER_SESSION || status != 0) {
            throw new IOException("Register Session failed: command=" + command + " status=" + status);
        }
        int length = Short.toUnsignedInt(buffer.getShort(2));
        if (length > 0) {
            readFully(length);
        }
        return Integer.toUnsignedLong(buffer.getInt(4));
    }

    /** Sends one encapsulation frame and returns the response payload. */
    private byte[] encapExchange(int command, byte[] payload) throws IOException {
        ByteBuffer header = ByteBuffer.allocate(24).order(ByteOrder.LITTLE_ENDIAN);
        header.putShort((short) command);
        header.putShort((short) payload.length);
        header.putInt((int) sessionHandle);
        header.putInt(0);
        header.putLong(0L);
        header.putInt(0);

        DataOutputStream out = new DataOutputStream(socket.getOutputStream());
        out.write(header.array());
        out.write(payload);
        out.flush();

        byte[] responseHeader = readFully(24);
        ByteBuffer buffer = ByteBuffer.wrap(responseHeader).order(ByteOrder.LITTLE_ENDIAN);
        int responseCommand = Short.toUnsignedInt(buffer.getShort(0));
        int length = Short.toUnsignedInt(buffer.getShort(2));
        int status = buffer.getInt(8);
        if (responseCommand != command) {
            throw new IOException("Unexpected encapsulation command 0x"
                    + Integer.toHexString(responseCommand) + " (expected 0x" + Integer.toHexString(command) + ")");
        }
        if (status != 0) {
            throw new IOException("Encapsulation error status " + status + " — session is likely dropped");
        }
        return length > 0 ? readFully(length) : new byte[0];
    }

    private byte[] readFully(int length) throws IOException {
        DataInputStream in = new DataInputStream(socket.getInputStream());
        byte[] data = in.readNBytes(length);
        if (data.length < length) {
            throw new EOFException("Incomplete EtherNet/IP frame: " + data.length + " of " + length + " bytes");
        }
        return data;
    }

    private static byte[] buildSendRrDataPayload(byte[] cipData) {
        ByteBuffer buffer = ByteBuffer.allocate(16 + cipData.length).order(ByteOrder.LITTLE_ENDIAN);
        buffer.putInt(0);                 // interface handle: 0 for CIP on TCP
        buffer.putShort((short) 10);      // timeout
        buffer.putShort((short) 2);       // item count
        buffer.putShort((short) CPF_NULL_ADDRESS);
        buffer.putShort((short) 0);
        buffer.putShort((short) CPF_UNCONNECTED_DATA);
        buffer.putShort((short) cipData.length);
        buffer.put(cipData);
        return buffer.array();
    }

    private static byte[] extractUnconnectedData(byte[] payload) throws IOException {
        ByteBuffer buffer = ByteBuffer.wrap(payload).order(ByteOrder.LITTLE_ENDIAN);
        if (payload.length < 8) {
            throw new IOException("Short SendRRData payload: " + payload.length + " bytes");
        }
        buffer.getInt();                  // interface handle
        buffer.getShort();                // timeout
        int itemCount = Short.toUnsignedInt(buffer.getShort());
        for (int i = 0; i < itemCount; i++) {
            int typeId = Short.toUnsignedInt(buffer.getShort());
            int length = Short.toUnsignedInt(buffer.getShort());
            if (typeId == CPF_UNCONNECTED_DATA) {
                byte[] data = new byte[length];
                buffer.get(data);
                return data;
            }
            buffer.position(buffer.position() + length);
        }
        throw new IOException("No Unconnected Data item in SendRRData response");
    }

    /** Builds a CIP request: service, symbolic tag path, then service-specific data. */
    private static byte[] buildCipRequest(int service, String tagPath, byte[] serviceData)
            throws DriverException {
        byte[] path = encodeSymbolicPath(tagPath);
        ByteBuffer buffer = ByteBuffer.allocate(2 + path.length + serviceData.length)
                .order(ByteOrder.LITTLE_ENDIAN);
        buffer.put((byte) service);
        buffer.put((byte) (path.length / 2));
        buffer.put(path);
        buffer.put(serviceData);
        return buffer.array();
    }

    /** ANSI extended symbolic segments (0x91) for each dot-separated name segment. */
    private static byte[] encodeSymbolicPath(String tagPath) throws DriverException {
        ByteArrayOutputStream path = new ByteArrayOutputStream();
        for (String segment : tagPath.split("\\.")) {
            byte[] name = segment.getBytes(StandardCharsets.US_ASCII);
            if (name.length == 0 || name.length > 255) {
                throw new DriverException("Invalid CIP tag path segment: '" + segment + "'");
            }
            path.write(0x91);
            path.write(name.length);
            path.write(name, 0, name.length);
            if (name.length % 2 == 1) {
                path.write(0);
            }
        }
        return path.toByteArray();
    }

    private static Object decodeValue(int type, ByteBuffer data) throws IOException {
        return switch (type) {
            case CIP_BOOL -> data.get() != 0;
            case CIP_SINT -> (int) data.get();
            case CIP_INT -> (int) data.getShort();
            case CIP_DINT -> data.getInt();
            case CIP_REAL -> data.getFloat();
            default -> throw new IOException("Unsupported CIP atomic type 0x" + Integer.toHexString(type));
        };
    }

    private int resolveWriteType(String pointId, Object raw) {
        Integer cached = tagTypes.get(pointId);
        if (cached != null) {
            return cached;
        }
        if (raw instanceof Boolean) {
            return CIP_BOOL;
        }
        if (raw instanceof Float || raw instanceof Double) {
            return CIP_REAL;
        }
        return CIP_DINT;
    }

    private static byte[] encodeWriteData(int type, Object raw) throws DriverException {
        String text = raw.toString();
        ByteBuffer buffer = ByteBuffer.allocate(8).order(ByteOrder.LITTLE_ENDIAN);
        buffer.putShort((short) type);
        buffer.putShort((short) 1);       // element count
        try {
            switch (type) {
                case CIP_BOOL -> buffer.put((byte) (Boolean.parseBoolean(text) ? 1 : 0));
                case CIP_SINT -> buffer.put((byte) Integer.parseInt(text));
                case CIP_INT -> buffer.putShort((short) Integer.parseInt(text));
                case CIP_DINT -> buffer.putInt((int) Long.parseLong(text));
                case CIP_REAL -> buffer.putFloat(Float.parseFloat(text));
                default -> throw new DriverException(
                        "Unsupported CIP write type 0x" + Integer.toHexString(type));
            }
        } catch (NumberFormatException e) {
            throw new DriverException("Value '" + text + "' does not fit CIP type 0x"
                    + Integer.toHexString(type), e);
        }
        int length = switch (type) {
            case CIP_BOOL, CIP_SINT -> 5;
            case CIP_INT -> 6;
            default -> 8;
        };
        byte[] data = new byte[length];
        System.arraycopy(buffer.array(), 0, data, 0, length);
        return data;
    }

    private void markDisconnected() {
        connected = false;
        closeSocket();
    }

    private void closeSocket() {
        if (socket != null) {
            try {
                socket.close();
            } catch (IOException ignored) {
                // best effort
            }
            socket = null;
        }
    }

    private void readConfig(String name, java.util.function.Consumer<String> consumer) {
        driverObject.getVariable(name).ifPresent(record -> {
            Object raw = record.firstRow().get("raw");
            if (raw == null) {
                raw = record.firstRow().get("value");
            }
            if (raw != null) {
                consumer.accept(raw.toString());
            }
        });
    }
}
