package com.ispf.driver.rockwelldf1;

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
 * Allen-Bradley DF1 driver — protected-mode binary lab over a TCP serial bridge.
 * <p>
 * Default port {@code 2222}. Point mapping: {@code N7:0}, {@code F8:1}, {@code B3:0/0}.
 * Optional write maps {@code value}/{@code raw} to a typed logical write.
 * <p>
 * <strong>Honesty:</strong> TCP bridge full-duplex DF1 lab (Apache-2.0 clean-room), not a native
 * serial DF1 exclusive-owner stack and not EtherNet/IP CIP. JDK sockets only — no PLC4X,
 * no Rockwell/vendor SDKs. Subset: CMD {@code 0x0F} typed read/write for N/F/B files only.
 */
public class RockwellDf1DeviceDriver implements DeviceDriver {

    private static final DataSchema VALUE_SCHEMA = DataSchema.builder("df1Value")
            .field("value", FieldType.STRING)
            .field("file", FieldType.STRING)
            .field("element", FieldType.INTEGER)
            .field("bit", FieldType.INTEGER)
            .build();

    private static final DriverMetadata METADATA = new DriverMetadata(
            "rockwell-df1",
            "Rockwell DF1 Driver",
            "0.1.0",
            "DF1 protected-mode binary typed read/write (N/F/B) over TCP bridge lab"
                    + " (not native serial exclusive-owner; not EtherNet/IP CIP)",
            "ISPF",
            Map.of(
                    "host", "127.0.0.1",
                    "port", "2222",
                    "timeoutMs", "3000",
                    "dst", "1",
                    "src", "0"
            ),
            null,
            Set.of("read", "write")
    );

    private DriverObject driverObject;
    private String host = "127.0.0.1";
    private int port = 2222;
    private int timeoutMs = 3000;
    private int dst = 1;
    private int src = 0;
    private final AtomicInteger tns = new AtomicInteger(1);
    private final Map<String, RockwellDf1Point> points = new ConcurrentHashMap<>();
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
            case "dst" -> dst = Integer.parseInt(value.trim());
            case "src" -> src = Integer.parseInt(value.trim());
            default -> { }
        }
    }

    @Override
    public void connect() throws DriverException {
        connected = true;
        driverObject.log(DriverLogLevel.INFO, "Rockwell DF1 TCP-bridge lab ready for " + host + ":" + port);
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
            RockwellDf1Point point = RockwellDf1Point.parse(entry.getValue());
            points.put(entry.getKey(), point);
            driverObject.updateVariable(entry.getKey(), readElement(point));
        }
    }

    @Override
    public void writePoint(String pointId, DataRecord value) throws DriverException {
        if (!isConnected()) {
            throw new DriverException("Not connected");
        }
        RockwellDf1Point point = points.get(pointId);
        if (point == null) {
            throw new DriverException("Unknown point: " + pointId);
        }
        byte[] encoded = encodeWrite(point, value);
        exchange(RockwellDf1Frame.buildTypedWrite(dst, src, nextTns(), point, encoded));
        String display = displayValue(point, encoded);
        driverObject.updateVariable(pointId, DataRecord.single(VALUE_SCHEMA, Map.of(
                "value", display,
                "file", point.deviceLabel(),
                "element", point.element(),
                "bit", point.bit()
        )));
    }

    private DataRecord readElement(RockwellDf1Point point) throws DriverException {
        byte[] reply = exchange(RockwellDf1Frame.buildTypedRead(dst, src, nextTns(), point));
        RockwellDf1Frame.ParsedPdu pdu = RockwellDf1Frame.parsePdu(reply);
        if (pdu.sts() != RockwellDf1Frame.STS_OK) {
            throw new DriverException("DF1 STS 0x" + Integer.toHexString(pdu.sts() & 0xFF));
        }
        String display = displayValue(point, pdu.payload());
        return DataRecord.single(VALUE_SCHEMA, Map.of(
                "value", display,
                "file", point.deviceLabel(),
                "element", point.element(),
                "bit", point.bit()
        ));
    }

    private byte[] exchange(byte[] request) throws DriverException {
        try (Socket socket = new Socket()) {
            socket.connect(new InetSocketAddress(host, port), timeoutMs);
            socket.setSoTimeout(timeoutMs);
            OutputStream out = socket.getOutputStream();
            InputStream in = socket.getInputStream();
            out.write(request);
            out.flush();
            return RockwellDf1Frame.readFrame(in);
        } catch (IOException e) {
            throw new DriverException("Rockwell DF1 I/O failed for " + host + ":" + port, e);
        }
    }

    private int nextTns() {
        return tns.getAndIncrement() & 0xFFFF;
    }

    private static byte[] encodeWrite(RockwellDf1Point point, DataRecord value) {
        return switch (point.fileType()) {
            case N -> RockwellDf1Frame.encodeInt16((int) extractNumeric(value));
            case F -> RockwellDf1Frame.encodeFloat((float) extractDouble(value));
            case B -> {
                int word = (int) extractNumeric(value) & 0xFFFF;
                if (word != 0 && word != 1) {
                    // allow full word write or 0/1 bit semantics
                    yield RockwellDf1Frame.encodeInt16(word);
                }
                // 0/1 bit write: store as word with only that bit set/cleared representation
                yield RockwellDf1Frame.encodeInt16(word == 0 ? 0 : (1 << point.bit()));
            }
        };
    }

    private static String displayValue(RockwellDf1Point point, byte[] data) {
        return switch (point.fileType()) {
            case N -> String.valueOf(RockwellDf1Frame.decodeInt16(data));
            case F -> String.valueOf(RockwellDf1Frame.decodeFloat(data));
            case B -> {
                int word = RockwellDf1Frame.decodeInt16(data);
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
        throw new IllegalArgumentException("DF1 write requires numeric raw/value field");
    }

    private static double extractDouble(DataRecord value) {
        Object candidate = firstValue(value);
        if (candidate instanceof Number number) {
            return number.doubleValue();
        }
        if (candidate != null) {
            return Double.parseDouble(String.valueOf(candidate).trim());
        }
        throw new IllegalArgumentException("DF1 write requires numeric raw/value field");
    }

    private static Object firstValue(DataRecord value) {
        if (value == null || value.rowCount() == 0) {
            throw new IllegalArgumentException("DF1 write requires a value");
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
