package com.ispf.driver.knxtp;

import com.ispf.core.model.DataRecord;
import com.ispf.core.model.DataSchema;
import com.ispf.core.model.FieldType;
import com.ispf.driver.DeviceDriver;
import com.ispf.driver.DriverException;
import com.ispf.driver.DriverMetadata;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.SocketTimeoutException;
import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * KNXnet/IP Tunneling client — SEARCH (0x0201) plus GroupValue Read/Write over UDP.
 * <p>
 * Point mapping is a group address {@code main/middle/sub} or {@code main/sub}. This pack speaks
 * public KNXnet/IP framing ({@code 06 10}, service, total length) and cEMI L_Data; it is not a
 * Weinzierl proprietary stack and does not drive KNX Twisted-Pair PHY UART. Clean-room ISPF code,
 * Apache-2.0.
 */
public class KnxTpDeviceDriver implements DeviceDriver {

    /** KNXnet/IP SEARCH_REQUEST with null UDP HPAI — exact octets. */
    static final byte[] SEARCH_REQUEST = new byte[] {
            0x06, 0x10, 0x02, 0x01, 0x00, 0x0E, 0x08, 0x01, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00
    };

    static final int SERVICE_SEARCH_REQUEST = 0x0201;
    static final int SERVICE_SEARCH_RESPONSE = 0x0202;
    static final int SERVICE_CONNECT_REQUEST = 0x0205;
    static final int SERVICE_CONNECT_RESPONSE = 0x0206;
    static final int SERVICE_TUNNELLING_REQUEST = 0x0420;
    static final int SERVICE_TUNNELLING_ACK = 0x0421;

    static final byte CEMI_L_DATA_REQ = 0x11;
    static final byte CEMI_L_DATA_IND = 0x29;
    static final int APCI_GROUP_READ = 0x0000;
    static final int APCI_GROUP_RESPONSE = 0x0040;
    static final int APCI_GROUP_WRITE = 0x0080;

    private static final DataSchema VALUE_SCHEMA = DataSchema.builder("knxTpValue")
            .field("value", FieldType.STRING)
            .field("groupAddress", FieldType.STRING)
            .field("raw", FieldType.INTEGER)
            .build();

    private static final DriverMetadata METADATA = new DriverMetadata(
            "knx-tp",
            "KNXnet/IP Driver",
            "0.1.0",
            "KNXnet/IP SEARCH + Tunneling GroupValue Read/Write over UDP",
            "ISPF",
            Map.of(
                    "host", "127.0.0.1",
                    "port", "3671",
                    "timeoutMs", "3000",
                    "sourceAddress", "0.0.0"
            ),
            null,
            Set.of("read", "write")
    );

    private DriverObject driverObject;
    private String host = "127.0.0.1";
    private int port = 3671;
    private int timeoutMs = 3000;
    private int sourceAddress = 0;
    private final Map<String, KnxTpPoint> points = new ConcurrentHashMap<>();
    private final AtomicInteger sequence = new AtomicInteger();
    private volatile boolean connected;
    private DatagramSocket socket;
    private InetSocketAddress remote;
    private int channelId = -1;

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
            case "sourceAddress" -> sourceAddress = parseIndividualAddress(value.trim());
            default -> { }
        }
    }

    @Override
    public synchronized void connect() throws DriverException {
        disconnect();
        try {
            socket = new DatagramSocket();
            socket.setSoTimeout(timeoutMs);
            remote = new InetSocketAddress(InetAddress.getByName(host), port);

            send(SEARCH_REQUEST);
            byte[] searchResp = receiveMatching(SERVICE_SEARCH_RESPONSE);
            if (searchResp == null) {
                throw new DriverException("KNXnet/IP SEARCH_RESPONSE timeout from " + host + ":" + port);
            }

            byte[] localIp = socket.getLocalAddress().getAddress();
            if (localIp.length != 4) {
                localIp = new byte[] { 127, 0, 0, 1 };
            }
            byte[] hpai = udpHpai(localIp, socket.getLocalPort());
            send(connectRequest(hpai, hpai));
            byte[] connectResp = receiveMatching(SERVICE_CONNECT_RESPONSE);
            if (connectResp == null) {
                throw new DriverException("KNXnet/IP CONNECT_RESPONSE timeout from " + host + ":" + port);
            }
            if (connectResp.length < 8 || (connectResp[7] & 0xFF) != 0) {
                throw new DriverException("KNXnet/IP CONNECT failed");
            }
            channelId = connectResp[6] & 0xFF;
            sequence.set(0);
            connected = true;
            driverObject.log(DriverLogLevel.INFO,
                    "KNXnet/IP Tunneling ready for " + host + ":" + port + " channel=" + channelId);
        } catch (DriverException e) {
            closeQuietly();
            throw e;
        } catch (IOException e) {
            closeQuietly();
            throw new DriverException("KNXnet/IP connect failed for " + host + ":" + port, e);
        }
    }

    @Override
    public synchronized void disconnect() {
        connected = false;
        closeQuietly();
        points.clear();
        channelId = -1;
    }

    @Override
    public boolean isConnected() {
        return connected;
    }

    @Override
    public synchronized void readPoints(Map<String, String> pointMappings) throws DriverException {
        if (!isConnected()) {
            throw new DriverException("Not connected");
        }
        points.clear();
        for (Map.Entry<String, String> entry : pointMappings.entrySet()) {
            KnxTpPoint point = KnxTpPoint.parse(entry.getValue());
            points.put(entry.getKey(), point);
            int raw = groupRead(point);
            driverObject.updateVariable(entry.getKey(), DataRecord.single(VALUE_SCHEMA, Map.of(
                    "value", String.valueOf(raw),
                    "groupAddress", point.addressText(),
                    "raw", raw
            )));
        }
    }

    @Override
    public synchronized void writePoint(String pointId, DataRecord value) throws DriverException {
        if (!isConnected()) {
            throw new DriverException("Not connected");
        }
        KnxTpPoint point = points.get(pointId);
        if (point == null) {
            throw new DriverException("Unknown point: " + pointId);
        }
        int raw = (int) extractNumeric(value) & 0xFF;
        groupWrite(point, raw);
        driverObject.updateVariable(pointId, DataRecord.single(VALUE_SCHEMA, Map.of(
                "value", String.valueOf(raw),
                "groupAddress", point.addressText(),
                "raw", raw
        )));
    }

    private int groupRead(KnxTpPoint point) throws DriverException {
        try {
            int seq = sequence.getAndIncrement() & 0xFF;
            byte[] cemi = buildCemiWithSource(CEMI_L_DATA_REQ, point.groupAddress(), APCI_GROUP_READ, null);
            send(tunnellingRequest(channelId, seq, cemi));

            Integer value = null;
            long deadline = System.currentTimeMillis() + timeoutMs;
            while (System.currentTimeMillis() < deadline && value == null) {
                byte[] frame = receiveRaw(Math.max(1, (int) (deadline - System.currentTimeMillis())));
                if (frame == null) {
                    continue;
                }
                int service = serviceType(frame);
                if (service == SERVICE_TUNNELLING_ACK) {
                    continue;
                }
                if (service == SERVICE_TUNNELLING_REQUEST && frame.length >= 10) {
                    int ackChannel = frame[7] & 0xFF;
                    int ackSeq = frame[8] & 0xFF;
                    send(tunnellingAck(ackChannel, ackSeq, 0));
                    byte[] cemiResp = Arrays.copyOfRange(frame, 10, frame.length);
                    if (matchesGroupResponse(cemiResp, point.groupAddress())) {
                        value = extractDataByteFromCemi(cemiResp);
                    }
                }
            }
            if (value == null) {
                throw new DriverException("KNXnet/IP timed out waiting for group response");
            }
            return value;
        } catch (DriverException e) {
            throw e;
        } catch (IOException e) {
            throw new DriverException("KNXnet/IP I/O failed for " + host + ":" + port, e);
        }
    }

    private void groupWrite(KnxTpPoint point, int value) throws DriverException {
        try {
            int seq = sequence.getAndIncrement() & 0xFF;
            byte[] cemi = buildCemiWithSource(CEMI_L_DATA_REQ, point.groupAddress(), APCI_GROUP_WRITE, new byte[] {
                    (byte) (value & 0xFF)
            });
            send(tunnellingRequest(channelId, seq, cemi));
            receiveMatching(SERVICE_TUNNELLING_ACK);
        } catch (IOException e) {
            throw new DriverException("KNXnet/IP write failed for " + host + ":" + port, e);
        }
    }

    private void send(byte[] frame) throws IOException {
        socket.send(new DatagramPacket(frame, frame.length, remote));
    }

    private byte[] receiveMatching(int expectedService) throws IOException {
        long deadline = System.currentTimeMillis() + timeoutMs;
        while (System.currentTimeMillis() <= deadline) {
            byte[] frame = receiveRaw(Math.max(1, (int) (deadline - System.currentTimeMillis())));
            if (frame != null && serviceType(frame) == expectedService) {
                return frame;
            }
        }
        return null;
    }

    private byte[] receiveRaw(int soTimeoutMs) throws IOException {
        socket.setSoTimeout(Math.max(1, soTimeoutMs));
        byte[] buffer = new byte[512];
        DatagramPacket packet = new DatagramPacket(buffer, buffer.length);
        try {
            socket.receive(packet);
        } catch (SocketTimeoutException e) {
            return null;
        }
        return Arrays.copyOf(packet.getData(), packet.getLength());
    }

    static int serviceType(byte[] frame) {
        if (frame == null || frame.length < 6) {
            return -1;
        }
        return ((frame[2] & 0xFF) << 8) | (frame[3] & 0xFF);
    }

    static void writeHeader(ByteBuffer buf, int serviceType, int totalLength) {
        buf.put((byte) 0x06);
        buf.put((byte) 0x10);
        buf.putShort((short) serviceType);
        buf.putShort((short) totalLength);
    }

    static byte[] udpHpai(byte[] ipv4, int port) {
        return new byte[] {
                0x08,
                0x01,
                ipv4[0], ipv4[1], ipv4[2], ipv4[3],
                (byte) ((port >> 8) & 0xFF),
                (byte) (port & 0xFF)
        };
    }

    static byte[] connectRequest(byte[] controlHpai, byte[] dataHpai) {
        byte[] cri = new byte[] { 0x04, 0x04, 0x02, 0x00 };
        int total = 6 + controlHpai.length + dataHpai.length + cri.length;
        ByteBuffer buf = ByteBuffer.allocate(total);
        writeHeader(buf, SERVICE_CONNECT_REQUEST, total);
        buf.put(controlHpai);
        buf.put(dataHpai);
        buf.put(cri);
        return buf.array();
    }

    static byte[] tunnellingRequest(int channelId, int sequence, byte[] cemi) {
        int total = 6 + 4 + cemi.length;
        ByteBuffer buf = ByteBuffer.allocate(total);
        writeHeader(buf, SERVICE_TUNNELLING_REQUEST, total);
        buf.put((byte) 0x04);
        buf.put((byte) (channelId & 0xFF));
        buf.put((byte) (sequence & 0xFF));
        buf.put((byte) 0x00);
        buf.put(cemi);
        return buf.array();
    }

    static byte[] tunnellingAck(int channelId, int sequence, int status) {
        ByteBuffer buf = ByteBuffer.allocate(10);
        writeHeader(buf, SERVICE_TUNNELLING_ACK, 10);
        buf.put((byte) 0x04);
        buf.put((byte) (channelId & 0xFF));
        buf.put((byte) (sequence & 0xFF));
        buf.put((byte) (status & 0xFF));
        return buf.array();
    }

    static byte[] buildCemi(byte messageCode, int groupAddress, int apci, byte[] data) {
        int dataLen = data == null ? 0 : data.length;
        int apduLen = 2 + dataLen;
        ByteBuffer cemi = ByteBuffer.allocate(9 + apduLen);
        cemi.put(messageCode);
        cemi.put((byte) 0x00);
        cemi.put((byte) 0xBC);
        cemi.put((byte) 0xE0);
        cemi.putShort((short) 0);
        cemi.putShort((short) (groupAddress & 0xFFFF));
        cemi.put((byte) (apduLen & 0xFF));
        cemi.put((byte) ((apci >> 8) & 0xFF));
        cemi.put((byte) (apci & 0xFF));
        if (data != null) {
            cemi.put(data);
        }
        return cemi.array();
    }

    byte[] buildCemiWithSource(byte messageCode, int groupAddress, int apci, byte[] data) {
        byte[] cemi = buildCemi(messageCode, groupAddress, apci, data);
        cemi[4] = (byte) ((sourceAddress >> 8) & 0xFF);
        cemi[5] = (byte) (sourceAddress & 0xFF);
        return cemi;
    }

    static boolean matchesGroupResponse(byte[] cemi, int expectedGa) {
        if (cemi == null || cemi.length < 11) {
            return false;
        }
        byte code = cemi[0];
        if (code != CEMI_L_DATA_IND && code != CEMI_L_DATA_REQ) {
            return false;
        }
        int addInfo = cemi[1] & 0xFF;
        int base = 2 + addInfo;
        if (cemi.length < base + 9) {
            return false;
        }
        int ga = ((cemi[base + 4] & 0xFF) << 8) | (cemi[base + 5] & 0xFF);
        if (ga != (expectedGa & 0xFFFF)) {
            return false;
        }
        int apci = ((cemi[base + 7] & 0xFF) << 8) | (cemi[base + 8] & 0xFF);
        return (apci & 0x03C0) == (APCI_GROUP_RESPONSE & 0x03C0);
    }

    static int extractDataByteFromCemi(byte[] cemi) {
        int addInfo = cemi[1] & 0xFF;
        int base = 2 + addInfo;
        int lengthField = cemi[base + 6] & 0xFF;
        if (lengthField <= 2) {
            return cemi[base + 8] & 0x3F;
        }
        return cemi[base + 9] & 0xFF;
    }

    static int parseIndividualAddress(String text) {
        String[] parts = text.split("\\.");
        if (parts.length != 3) {
            throw new IllegalArgumentException("KNX individual address must be area.line.device");
        }
        int area = Integer.parseInt(parts[0].trim());
        int line = Integer.parseInt(parts[1].trim());
        int device = Integer.parseInt(parts[2].trim());
        return ((area & 0x0F) << 12) | ((line & 0x0F) << 8) | (device & 0xFF);
    }

    private void closeQuietly() {
        if (socket != null) {
            socket.close();
            socket = null;
        }
        remote = null;
    }

    private static long extractNumeric(DataRecord value) {
        if (value == null || value.rowCount() == 0) {
            throw new IllegalArgumentException("KNXnet/IP write requires a value");
        }
        Map<String, Object> row = value.firstRow();
        for (String key : List.of("raw", "value")) {
            Object candidate = row.get(key);
            if (candidate instanceof Number number) {
                return number.longValue();
            }
            if (candidate != null) {
                return Long.parseLong(String.valueOf(candidate).trim());
            }
        }
        throw new IllegalArgumentException("KNXnet/IP write requires numeric raw/value field");
    }
}
