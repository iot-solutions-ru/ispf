package com.ispf.driver.beckhoffads;

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
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Beckhoff ADS driver — AMS/TCP AdsRead (and optional AdsWrite) for INT/DINT/REAL/STRING.
 * <p>
 * Point mapping: {@code indexGroup:indexOffset:TYPE}, e.g. {@code 0xF020:0:INT} or
 * {@code 16416:4:STRING:32}. Uses public AMS/ADS TCP framing over JDK sockets only.
 * Limitations: no symbol-name resolution, no notifications, no ADS sum commands, no router AMS
 * discovery — index-group/offset subset only. Clean-room ISPF code, Apache-2.0.
 */
public class BeckhoffAdsDeviceDriver implements DeviceDriver {

    private static final int CMD_ADS_READ = 0x0002;
    private static final int CMD_ADS_WRITE = 0x0003;
    private static final int STATE_ADS_COMMAND = 0x0004;
    private static final int STATE_ADS_RESPONSE = 0x0005;

    private static final DataSchema VALUE_SCHEMA = DataSchema.builder("adsValue")
            .field("value", FieldType.STRING)
            .field("indexGroup", FieldType.STRING)
            .field("indexOffset", FieldType.STRING)
            .field("type", FieldType.STRING)
            .build();

    private static final DriverMetadata METADATA = new DriverMetadata(
            "beckhoff-ads",
            "Beckhoff ADS Driver",
            "0.1.0",
            "Reads/writes ADS variables by indexGroup/indexOffset over AMS/TCP (INT/DINT/REAL/STRING)",
            "ISPF",
            Map.of(
                    "host", "127.0.0.1",
                    "port", "48898",
                    "timeoutMs", "3000",
                    "targetAmsNetId", "127.0.0.1.1.1",
                    "targetAmsPort", "851",
                    "sourceAmsNetId", "127.0.0.1.1.1",
                    "sourceAmsPort", "32905"
            ),
            null,
            Set.of("read", "write")
    );

    private DriverObject driverObject;
    private String host = "127.0.0.1";
    private int port = 48898;
    private int timeoutMs = 3000;
    private byte[] targetNetId = parseNetId("127.0.0.1.1.1");
    private int targetPort = 851;
    private byte[] sourceNetId = parseNetId("127.0.0.1.1.1");
    private int sourcePort = 32905;
    private final AtomicInteger invokeId = new AtomicInteger();
    private final Map<String, BeckhoffAdsPoint> points = new ConcurrentHashMap<>();
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
            case "targetAmsNetId" -> targetNetId = parseNetId(value.trim());
            case "targetAmsPort" -> targetPort = Integer.parseInt(value.trim());
            case "sourceAmsNetId" -> sourceNetId = parseNetId(value.trim());
            case "sourceAmsPort" -> sourcePort = Integer.parseInt(value.trim());
            default -> { }
        }
    }

    @Override
    public void connect() throws DriverException {
        connected = true;
        driverObject.log(DriverLogLevel.INFO, "Beckhoff ADS ready for " + host + ":" + port);
    }

    @Override
    public void disconnect() {
        connected = false;
        points.clear();
    }

    @Override
    public boolean isConnected() {
        return connected;
    }

    @Override
    public void readPoints(Map<String, String> pointMappings) throws DriverException {
        if (!isConnected()) {
            throw new DriverException("Not connected");
        }
        points.clear();
        for (Map.Entry<String, String> entry : pointMappings.entrySet()) {
            BeckhoffAdsPoint point = BeckhoffAdsPoint.parse(entry.getValue());
            points.put(entry.getKey(), point);
            driverObject.updateVariable(entry.getKey(), readAds(point));
        }
    }

    @Override
    public void writePoint(String pointId, DataRecord value) throws DriverException {
        if (!isConnected()) {
            throw new DriverException("Not connected");
        }
        BeckhoffAdsPoint point = points.get(pointId);
        if (point == null) {
            throw new DriverException("Unknown point: " + pointId);
        }
        byte[] payload = encodeValue(point, value);
        writeAds(point, payload);
        driverObject.updateVariable(pointId, toRecord(point, decodeValue(point, payload)));
    }

    private DataRecord readAds(BeckhoffAdsPoint point) throws DriverException {
        ByteBuffer req = ByteBuffer.allocate(12).order(ByteOrder.LITTLE_ENDIAN);
        req.putInt((int) point.indexGroup());
        req.putInt((int) point.indexOffset());
        req.putInt(point.byteLength());
        byte[] responseData = transact(CMD_ADS_READ, req.array());
        if (responseData.length < 8) {
            throw new DriverException("ADS Read response too short");
        }
        ByteBuffer resp = ByteBuffer.wrap(responseData).order(ByteOrder.LITTLE_ENDIAN);
        int result = resp.getInt();
        if (result != 0) {
            throw new DriverException("ADS Read result 0x" + Integer.toHexString(result));
        }
        int length = resp.getInt();
        byte[] data = new byte[Math.min(length, responseData.length - 8)];
        resp.get(data);
        return toRecord(point, decodeValue(point, data));
    }

    private void writeAds(BeckhoffAdsPoint point, byte[] payload) throws DriverException {
        ByteBuffer req = ByteBuffer.allocate(12 + payload.length).order(ByteOrder.LITTLE_ENDIAN);
        req.putInt((int) point.indexGroup());
        req.putInt((int) point.indexOffset());
        req.putInt(payload.length);
        req.put(payload);
        byte[] responseData = transact(CMD_ADS_WRITE, req.array());
        if (responseData.length < 4) {
            throw new DriverException("ADS Write response too short");
        }
        int result = ByteBuffer.wrap(responseData).order(ByteOrder.LITTLE_ENDIAN).getInt();
        if (result != 0) {
            throw new DriverException("ADS Write result 0x" + Integer.toHexString(result));
        }
    }

    private byte[] transact(int commandId, byte[] adsData) throws DriverException {
        int invoke = invokeId.incrementAndGet();
        byte[] amsPacket = buildAmsPacket(commandId, STATE_ADS_COMMAND, adsData, 0, invoke);
        ByteBuffer tcp = ByteBuffer.allocate(6 + amsPacket.length).order(ByteOrder.LITTLE_ENDIAN);
        tcp.putShort((short) 0);
        tcp.putInt(amsPacket.length);
        tcp.put(amsPacket);

        try (Socket socket = new Socket()) {
            socket.connect(new InetSocketAddress(host, port), timeoutMs);
            socket.setSoTimeout(timeoutMs);
            OutputStream out = socket.getOutputStream();
            InputStream in = socket.getInputStream();
            out.write(tcp.array());
            out.flush();
            return readAmsResponse(in, invoke);
        } catch (IOException e) {
            throw new DriverException("Beckhoff ADS I/O failed for " + host + ":" + port, e);
        }
    }

    private byte[] buildAmsPacket(int commandId, int stateFlags, byte[] data, int errorCode, int invoke) {
        ByteBuffer buf = ByteBuffer.allocate(32 + data.length).order(ByteOrder.LITTLE_ENDIAN);
        buf.put(targetNetId);
        buf.putShort((short) (targetPort & 0xFFFF));
        buf.put(sourceNetId);
        buf.putShort((short) (sourcePort & 0xFFFF));
        buf.putShort((short) (commandId & 0xFFFF));
        buf.putShort((short) (stateFlags & 0xFFFF));
        buf.putInt(data.length);
        buf.putInt(errorCode);
        buf.putInt(invoke);
        buf.put(data);
        return buf.array();
    }

    private static byte[] readAmsResponse(InputStream in, int expectedInvoke) throws IOException {
        byte[] tcpHeader = in.readNBytes(6);
        if (tcpHeader.length < 6) {
            throw new IOException("Incomplete AMS/TCP header");
        }
        int length = (tcpHeader[2] & 0xFF)
                | ((tcpHeader[3] & 0xFF) << 8)
                | ((tcpHeader[4] & 0xFF) << 16)
                | ((tcpHeader[5] & 0xFF) << 24);
        if (length < 32) {
            throw new IOException("AMS packet too short: " + length);
        }
        byte[] ams = in.readNBytes(length);
        if (ams.length < length) {
            throw new IOException("Truncated AMS packet");
        }
        ByteBuffer buf = ByteBuffer.wrap(ams).order(ByteOrder.LITTLE_ENDIAN);
        buf.position(16);
        int commandId = buf.getShort() & 0xFFFF;
        int stateFlags = buf.getShort() & 0xFFFF;
        int cbData = buf.getInt();
        int errorCode = buf.getInt();
        int invoke = buf.getInt();
        if (errorCode != 0) {
            throw new IOException("AMS error 0x" + Integer.toHexString(errorCode));
        }
        if (invoke != expectedInvoke) {
            throw new IOException("ADS invoke mismatch: expected " + expectedInvoke + " got " + invoke);
        }
        if (stateFlags != STATE_ADS_RESPONSE && stateFlags != (STATE_ADS_COMMAND | 0x0001)) {
            throw new IOException("Unexpected AMS state flags 0x" + Integer.toHexString(stateFlags));
        }
        if (commandId != CMD_ADS_READ && commandId != CMD_ADS_WRITE) {
            throw new IOException("Unexpected ADS command 0x" + Integer.toHexString(commandId));
        }
        byte[] data = new byte[Math.min(cbData, ams.length - 32)];
        System.arraycopy(ams, 32, data, 0, data.length);
        return data;
    }

    private static DataRecord toRecord(BeckhoffAdsPoint point, String value) {
        return DataRecord.single(VALUE_SCHEMA, Map.of(
                "value", value,
                "indexGroup", "0x" + Long.toHexString(point.indexGroup()).toUpperCase(Locale.ROOT),
                "indexOffset", Long.toString(point.indexOffset()),
                "type", point.type().name()
        ));
    }

    private static String decodeValue(BeckhoffAdsPoint point, byte[] data) {
        ByteBuffer buf = ByteBuffer.wrap(data).order(ByteOrder.LITTLE_ENDIAN);
        return switch (point.type()) {
            case INT -> {
                if (data.length < 2) {
                    yield "0";
                }
                yield String.valueOf(buf.getShort());
            }
            case DINT -> {
                if (data.length < 4) {
                    yield "0";
                }
                yield String.valueOf(buf.getInt());
            }
            case REAL -> {
                if (data.length < 4) {
                    yield "0.0";
                }
                yield Float.toString(buf.getFloat());
            }
            case STRING -> {
                int end = 0;
                while (end < data.length && data[end] != 0) {
                    end++;
                }
                yield new String(data, 0, end, StandardCharsets.US_ASCII);
            }
        };
    }

    private static byte[] encodeValue(BeckhoffAdsPoint point, DataRecord value) {
        String text = extractText(value);
        return switch (point.type()) {
            case INT -> {
                ByteBuffer buf = ByteBuffer.allocate(2).order(ByteOrder.LITTLE_ENDIAN);
                buf.putShort((short) Integer.parseInt(text.trim()));
                yield buf.array();
            }
            case DINT -> {
                ByteBuffer buf = ByteBuffer.allocate(4).order(ByteOrder.LITTLE_ENDIAN);
                buf.putInt((int) Long.parseLong(text.trim()));
                yield buf.array();
            }
            case REAL -> {
                ByteBuffer buf = ByteBuffer.allocate(4).order(ByteOrder.LITTLE_ENDIAN);
                buf.putFloat(Float.parseFloat(text.trim()));
                yield buf.array();
            }
            case STRING -> {
                byte[] raw = text.getBytes(StandardCharsets.US_ASCII);
                byte[] out = new byte[point.byteLength()];
                int copy = Math.min(raw.length, point.byteLength() - 1);
                System.arraycopy(raw, 0, out, 0, Math.max(0, copy));
                yield out;
            }
        };
    }

    private static String extractText(DataRecord value) {
        if (value == null || value.rowCount() == 0) {
            throw new IllegalArgumentException("ADS write requires a value");
        }
        Map<String, Object> row = value.firstRow();
        for (String key : List.of("raw", "value")) {
            Object candidate = row.get(key);
            if (candidate != null) {
                return String.valueOf(candidate);
            }
        }
        throw new IllegalArgumentException("ADS write requires raw/value field");
    }

    static byte[] parseNetId(String netId) {
        String[] parts = netId.split("\\.");
        if (parts.length != 6) {
            throw new IllegalArgumentException("AMS NetId must have 6 octets, got: " + netId);
        }
        byte[] out = new byte[6];
        for (int i = 0; i < 6; i++) {
            int v = Integer.parseInt(parts[i].trim());
            if (v < 0 || v > 255) {
                throw new IllegalArgumentException("AMS NetId octet out of range: " + v);
            }
            out[i] = (byte) v;
        }
        return out;
    }

}
