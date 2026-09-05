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
import java.net.SocketTimeoutException;
import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * KNX TP via IP interface — public KNXnet/IP Routing Indication (UDP) group read/write.
 * <p>
 * Physical KNX Twisted-Pair UART is not exercised in CI. This driver speaks a clean-room
 * subset of KNXnet/IP Routing ({@code ROUTING_INDICATION} + cEMI {@code L_Data}) to an IP
 * interface or lab fake on {@code host:port}. It is not a Weinzierl proprietary stack and
 * does not implement full KNXnet/IP Tunneling management or EMI1/EMI2 serial framing.
 * Clean-room ISPF code, Apache-2.0.
 */
public class KnxTpDeviceDriver implements DeviceDriver {

    static final int SERVICE_ROUTING_INDICATION = 0x0530;
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
            "KNX TP via IP Driver",
            "0.1.0",
            "KNX group read/write via KNXnet/IP Routing Indication over UDP (TP via IP interface)",
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
            case "sourceAddress" -> sourceAddress = parseIndividualAddress(value.trim());
            default -> { }
        }
    }

    @Override
    public void connect() throws DriverException {
        connected = true;
        driverObject.log(DriverLogLevel.INFO, "KNX TP via IP ready for " + host + ":" + port);
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
    public void writePoint(String pointId, DataRecord value) throws DriverException {
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
        byte[] request = wrapRouting(buildCemiWithSource(CEMI_L_DATA_REQ, point.groupAddress(), APCI_GROUP_READ, null));
        byte[] response = transact(request, point.groupAddress(), APCI_GROUP_RESPONSE);
        return extractDataByte(response);
    }

    private void groupWrite(KnxTpPoint point, int value) throws DriverException {
        byte[] request = wrapRouting(buildCemiWithSource(CEMI_L_DATA_REQ, point.groupAddress(), APCI_GROUP_WRITE, new byte[] {
                (byte) (value & 0xFF)
        }));
        send(request);
    }

    private void send(byte[] frame) throws DriverException {
        try (DatagramSocket socket = new DatagramSocket()) {
            socket.setSoTimeout(timeoutMs);
            InetAddress address = InetAddress.getByName(host);
            socket.send(new DatagramPacket(frame, frame.length, address, port));
        } catch (IOException e) {
            throw new DriverException("KNX TP via IP send failed for " + host + ":" + port, e);
        }
    }

    private byte[] transact(byte[] request, int expectedGa, int expectedApci) throws DriverException {
        try (DatagramSocket socket = new DatagramSocket()) {
            socket.setSoTimeout(timeoutMs);
            InetAddress address = InetAddress.getByName(host);
            socket.send(new DatagramPacket(request, request.length, address, port));
            byte[] buffer = new byte[512];
            long deadline = System.currentTimeMillis() + timeoutMs;
            while (System.currentTimeMillis() <= deadline) {
                int remaining = (int) Math.max(1, deadline - System.currentTimeMillis());
                socket.setSoTimeout(remaining);
                DatagramPacket packet = new DatagramPacket(buffer, buffer.length);
                try {
                    socket.receive(packet);
                } catch (SocketTimeoutException e) {
                    break;
                }
                byte[] payload = Arrays.copyOf(packet.getData(), packet.getLength());
                if (matchesResponse(payload, expectedGa, expectedApci)) {
                    return payload;
                }
            }
            throw new DriverException("KNX TP via IP timed out waiting for group response");
        } catch (DriverException e) {
            throw e;
        } catch (IOException e) {
            throw new DriverException("KNX TP via IP I/O failed for " + host + ":" + port, e);
        }
    }

    static byte[] wrapRouting(byte[] cemi) {
        ByteBuffer frame = ByteBuffer.allocate(6 + cemi.length);
        frame.put((byte) 0x06);
        frame.put((byte) 0x10);
        frame.putShort((short) SERVICE_ROUTING_INDICATION);
        frame.putShort((short) (6 + cemi.length));
        frame.put(cemi);
        return frame.array();
    }

    static byte[] buildCemi(byte messageCode, int groupAddress, int apci, byte[] data) {
        int dataLen = data == null ? 0 : data.length;
        // APDU = 2-byte APCI (+ optional data). Length octet = APDU size.
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

    static boolean matchesResponse(byte[] frame, int expectedGa, int expectedApci) {
        if (frame.length < 6 + 10) {
            return false;
        }
        int service = ((frame[2] & 0xFF) << 8) | (frame[3] & 0xFF);
        if (service != SERVICE_ROUTING_INDICATION) {
            return false;
        }
        int cemiOffset = 6;
        byte code = frame[cemiOffset];
        if (code != CEMI_L_DATA_IND && code != CEMI_L_DATA_REQ) {
            return false;
        }
        int addInfo = frame[cemiOffset + 1] & 0xFF;
        int base = cemiOffset + 2 + addInfo;
        if (frame.length < base + 8) {
            return false;
        }
        int ga = ((frame[base + 2] & 0xFF) << 8) | (frame[base + 3] & 0xFF);
        if (ga != (expectedGa & 0xFFFF)) {
            return false;
        }
        int lengthField = frame[base + 4] & 0xFF;
        if (lengthField < 1 || frame.length < base + 5 + lengthField) {
            return false;
        }
        int apci = ((frame[base + 5] & 0xFF) << 8) | (frame[base + 6] & 0xFF);
        apci &= 0x03C0; // APCI bits
        return apci == (expectedApci & 0x03C0);
    }

    static int extractDataByte(byte[] frame) {
        int cemiOffset = 6;
        int addInfo = frame[cemiOffset + 1] & 0xFF;
        int base = cemiOffset + 2 + addInfo;
        int lengthField = frame[base + 4] & 0xFF;
        if (lengthField <= 2) {
            // no data octet — 6-bit payload may sit in low APCI bits
            return frame[base + 6] & 0x3F;
        }
        return frame[base + 7] & 0xFF;
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

    private static long extractNumeric(DataRecord value) {
        if (value == null || value.rowCount() == 0) {
            throw new IllegalArgumentException("KNX TP write requires a value");
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
        throw new IllegalArgumentException("KNX TP write requires numeric raw/value field");
    }
}
