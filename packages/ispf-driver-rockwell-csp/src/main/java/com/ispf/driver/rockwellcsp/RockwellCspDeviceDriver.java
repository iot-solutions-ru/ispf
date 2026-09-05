package com.ispf.driver.rockwellcsp;

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
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Allen-Bradley CSP (PCCC-over-Ethernet) driver — clean-room lab subset on TCP port {@code 2222}.
 * <p>
 * Point mapping: {@code N7:0}, {@code F8:1}, {@code B3:0/0}. Optional write maps {@code value}/
 * {@code raw} to a typed logical write.
 * <p>
 * <strong>Honesty:</strong> ISPF CSP-lab binary framing for typed N/F/B read/write
 * (Apache-2.0 clean-room). This is <strong>not</strong> EtherNet/IP CIP, <strong>not</strong> DF1
 * serial, and <strong>not</strong> a full CSPv4 / SLC/PLC-5 Ethernet stack. JDK sockets only —
 * no PLC4X, no Rockwell/vendor SDKs. See {@link RockwellCspFrame} for the documented lab header.
 */
public class RockwellCspDeviceDriver implements DeviceDriver {

    private static final DataSchema VALUE_SCHEMA = DataSchema.builder("cspValue")
            .field("value", FieldType.STRING)
            .field("file", FieldType.STRING)
            .field("element", FieldType.INTEGER)
            .field("bit", FieldType.INTEGER)
            .build();

    private static final DriverMetadata METADATA = new DriverMetadata(
            "rockwell-csp",
            "Rockwell CSP Driver",
            "0.1.0",
            "CSP (PCCC-over-Ethernet) lab typed read/write (N/F/B) on TCP 2222"
                    + " — not EtherNet/IP CIP; not DF1 serial; not full CSPv4",
            "ISPF",
            Map.of(
                    "host", "127.0.0.1",
                    "port", "2222",
                    "timeoutMs", "3000"
            ),
            null,
            Set.of("read", "write")
    );

    private DriverObject driverObject;
    private String host = "127.0.0.1";
    private int port = 2222;
    private int timeoutMs = 3000;
    private final AtomicInteger tns = new AtomicInteger(1);
    private final Map<String, RockwellCspPoint> points = new ConcurrentHashMap<>();
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
        connected = true;
        driverObject.log(DriverLogLevel.INFO,
                "Rockwell CSP-lab ready for " + host + ":" + port
                        + " (PCCC-over-Ethernet lab subset — not EtherNet/IP CIP / DF1 serial)");
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
            RockwellCspPoint point = RockwellCspPoint.parse(entry.getValue());
            points.put(entry.getKey(), point);
            driverObject.updateVariable(entry.getKey(), readElement(point));
        }
    }

    @Override
    public void writePoint(String pointId, DataRecord value) throws DriverException {
        if (!isConnected()) {
            throw new DriverException("Not connected");
        }
        RockwellCspPoint point = points.get(pointId);
        if (point == null) {
            throw new DriverException("Unknown point: " + pointId);
        }
        byte[] encoded = encodeWrite(point, value);
        RockwellCspFrame.ParsedFrame reply = exchange(
                RockwellCspFrame.buildTypedWrite(nextTns(), point, encoded));
        if (reply.cmd() != RockwellCspFrame.CMD_TYPED_WRITE_REPLY) {
            throw new DriverException("Unexpected CSP write reply cmd 0x"
                    + Integer.toHexString(reply.cmd() & 0xFF));
        }
        if (reply.payload().length < 1 || reply.payload()[0] != RockwellCspFrame.STS_OK) {
            int sts = reply.payload().length > 0 ? reply.payload()[0] & 0xFF : -1;
            throw new DriverException("CSP STS 0x" + Integer.toHexString(sts));
        }
        String display = displayValue(point, encoded);
        driverObject.updateVariable(pointId, DataRecord.single(VALUE_SCHEMA, Map.of(
                "value", display,
                "file", point.deviceLabel(),
                "element", point.element(),
                "bit", point.bit()
        )));
    }

    private DataRecord readElement(RockwellCspPoint point) throws DriverException {
        RockwellCspFrame.ParsedFrame reply = exchange(
                RockwellCspFrame.buildTypedRead(nextTns(), point));
        if (reply.cmd() != RockwellCspFrame.CMD_TYPED_READ_REPLY) {
            throw new DriverException("Unexpected CSP read reply cmd 0x"
                    + Integer.toHexString(reply.cmd() & 0xFF));
        }
        if (reply.payload().length < 1 || reply.payload()[0] != RockwellCspFrame.STS_OK) {
            int sts = reply.payload().length > 0 ? reply.payload()[0] & 0xFF : -1;
            throw new DriverException("CSP STS 0x" + Integer.toHexString(sts));
        }
        byte[] data = RockwellCspFrame.replyData(reply.payload());
        String display = displayValue(point, data);
        return DataRecord.single(VALUE_SCHEMA, Map.of(
                "value", display,
                "file", point.deviceLabel(),
                "element", point.element(),
                "bit", point.bit()
        ));
    }

    private RockwellCspFrame.ParsedFrame exchange(byte[] request) throws DriverException {
        try (Socket socket = new Socket()) {
            socket.connect(new InetSocketAddress(host, port), timeoutMs);
            socket.setSoTimeout(timeoutMs);
            OutputStream out = socket.getOutputStream();
            InputStream in = socket.getInputStream();
            out.write(request);
            out.flush();
            return RockwellCspFrame.readFrame(in);
        } catch (IOException e) {
            throw new DriverException("Rockwell CSP I/O failed for " + host + ":" + port, e);
        }
    }

    private int nextTns() {
        return tns.getAndIncrement() & 0xFFFF;
    }

    private static byte[] encodeWrite(RockwellCspPoint point, DataRecord value) {
        return switch (point.fileType()) {
            case N -> RockwellCspFrame.encodeInt16((int) extractNumeric(value));
            case F -> RockwellCspFrame.encodeFloat((float) extractDouble(value));
            case B -> {
                int word = (int) extractNumeric(value) & 0xFFFF;
                if (word != 0 && word != 1) {
                    yield RockwellCspFrame.encodeInt16(word);
                }
                yield RockwellCspFrame.encodeInt16(word == 0 ? 0 : (1 << point.bit()));
            }
        };
    }

    private static String displayValue(RockwellCspPoint point, byte[] data) {
        return switch (point.fileType()) {
            case N -> String.valueOf(RockwellCspFrame.decodeInt16(data));
            case F -> String.valueOf(RockwellCspFrame.decodeFloat(data));
            case B -> {
                int word = RockwellCspFrame.decodeInt16(data);
                yield String.valueOf(((word >> point.bit()) & 1));
            }
        };
    }

    private static long extractNumeric(DataRecord value) {
        Object candidate = firstValue(value);
        if (candidate instanceof Number number) {
            return number.longValue();
        }
        if (candidate != null) {
            return Long.parseLong(String.valueOf(candidate).trim());
        }
        throw new IllegalArgumentException("CSP write requires numeric raw/value field");
    }

    private static double extractDouble(DataRecord value) {
        Object candidate = firstValue(value);
        if (candidate instanceof Number number) {
            return number.doubleValue();
        }
        if (candidate != null) {
            return Double.parseDouble(String.valueOf(candidate).trim());
        }
        throw new IllegalArgumentException("CSP write requires numeric raw/value field");
    }

    private static Object firstValue(DataRecord value) {
        if (value == null || value.rowCount() == 0) {
            throw new IllegalArgumentException("CSP write requires a value");
        }
        Map<String, Object> row = value.firstRow();
        for (String key : List.of("raw", "value")) {
            Object candidate = row.get(key);
            if (candidate != null) {
                return candidate;
            }
        }
        return null;
    }
}
