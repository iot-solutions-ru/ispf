package com.ispf.driver.lsxgt;

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
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * LS Electric XGT FEnet <strong>lab subset</strong> driver — binary framing over TCP (default port 2004).
 * <p>
 * This is an honest <strong>XGT-lab</strong> codec, not a certified LS FEnet stack. Frames use the
 * publicly known company-header magic {@code LSIS-XGT\0} plus a simplified application body
 * (invoke id, command, device type, address, count, optional payload). Proprietary FEnet command
 * layouts beyond this subset are intentionally omitted.
 * <p>
 * Frame (little-endian multi-byte fields):
 * <pre>
 *   0..9   magic "LSIS-XGT\0" (10 bytes)
 *   10..11 invokeId (uint16 LE)
 *   12     command: 0x01 READ, 0x02 WRITE
 *   13     deviceType: 0x01 DW, 0x02 MW, 0x03 MX
 *   14..17 address (uint32 LE)
 *   18..19 count (uint16 LE) — number of 16-bit words (MX always 1)
 *   20..   WRITE request / READ response: count × uint16 LE word values
 * </pre>
 * Point mapping: {@code %DW100}, {@code DW100}, {@code %MW10}, {@code %MX0} — see {@link LsXgtPoint}.
 * Clean-room ISPF code, Apache-2.0 — JDK sockets only; no PLC4X, no vendor SDK, no GPL.
 */
public class LsXgtDeviceDriver implements DeviceDriver {

    static final byte[] MAGIC = "LSIS-XGT\0\0".getBytes(StandardCharsets.US_ASCII); // 10-byte company header
    static final int HEADER_LEN = 20;
    static final byte CMD_READ = 0x01;
    static final byte CMD_WRITE = 0x02;

    private static final DataSchema VALUE_SCHEMA = DataSchema.builder("lsXgtValue")
            .field("value", FieldType.STRING)
            .field("device", FieldType.STRING)
            .field("address", FieldType.INTEGER)
            .field("count", FieldType.INTEGER)
            .build();

    private static final DriverMetadata METADATA = new DriverMetadata(
            "ls-xgt",
            "LS XGT Driver",
            "0.1.0",
            "XGT-lab binary read/write for %DW/%MW/%MX over TCP (not certified FEnet)",
            "ISPF",
            Map.of(
                    "host", "127.0.0.1",
                    "port", "2004",
                    "timeoutMs", "3000"
            ),
            null,
            Set.of("read", "write")
    );

    private DriverObject driverObject;
    private String host = "127.0.0.1";
    private int port = 2004;
    private int timeoutMs = 3000;
    private final AtomicInteger invokeId = new AtomicInteger();
    private final Map<String, LsXgtPoint> points = new ConcurrentHashMap<>();
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
        driverObject.log(DriverLogLevel.INFO, "LS XGT-lab ready for " + host + ":" + port);
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
            LsXgtPoint point = LsXgtPoint.parse(entry.getValue());
            points.put(entry.getKey(), point);
            driverObject.updateVariable(entry.getKey(), readDevice(point));
        }
    }

    @Override
    public void writePoint(String pointId, DataRecord value) throws DriverException {
        if (!isConnected()) {
            throw new DriverException("Not connected");
        }
        LsXgtPoint point = points.get(pointId);
        if (point == null) {
            throw new DriverException("Unknown point: " + pointId);
        }
        int word = (int) extractNumeric(value) & 0xFFFF;
        writeDevice(point, word);
        driverObject.updateVariable(pointId, DataRecord.single(VALUE_SCHEMA, Map.of(
                "value", String.valueOf(word),
                "device", point.deviceType().name(),
                "address", point.address(),
                "count", 1
        )));
    }

    private DataRecord readDevice(LsXgtPoint point) throws DriverException {
        byte[] request = buildHeader(CMD_READ, point, invokeId.incrementAndGet() & 0xFFFF);
        byte[] response = transact(request);
        validateHeader(response, CMD_READ, point);
        if (response.length < HEADER_LEN + point.count() * 2) {
            throw new DriverException("Truncated XGT-lab read payload");
        }
        StringBuilder sb = new StringBuilder();
        ByteBuffer data = ByteBuffer.wrap(response, HEADER_LEN, point.count() * 2).order(ByteOrder.LITTLE_ENDIAN);
        for (int i = 0; i < point.count(); i++) {
            if (i > 0) {
                sb.append(',');
            }
            sb.append(data.getShort() & 0xFFFF);
        }
        return DataRecord.single(VALUE_SCHEMA, Map.of(
                "value", sb.toString(),
                "device", point.deviceType().name(),
                "address", point.address(),
                "count", point.count()
        ));
    }

    private void writeDevice(LsXgtPoint point, int word) throws DriverException {
        LsXgtPoint single = new LsXgtPoint(point.deviceType(), point.address(), 1);
        ByteBuffer request = ByteBuffer.allocate(HEADER_LEN + 2).order(ByteOrder.LITTLE_ENDIAN);
        request.put(buildHeader(CMD_WRITE, single, invokeId.incrementAndGet() & 0xFFFF));
        request.putShort((short) (word & 0xFFFF));
        byte[] response = transact(request.array());
        validateHeader(response, CMD_WRITE, single);
    }

    static byte[] buildHeader(byte command, LsXgtPoint point, int invoke) {
        ByteBuffer buf = ByteBuffer.allocate(HEADER_LEN).order(ByteOrder.LITTLE_ENDIAN);
        buf.put(MAGIC);
        buf.putShort((short) (invoke & 0xFFFF));
        buf.put(command);
        buf.put(point.deviceType().code());
        buf.putInt(point.address());
        buf.putShort((short) point.count());
        return buf.array();
    }

    private void validateHeader(byte[] frame, byte expectedCommand, LsXgtPoint point) throws DriverException {
        if (frame.length < HEADER_LEN) {
            throw new DriverException("Truncated XGT-lab header");
        }
        if (!Arrays.equals(Arrays.copyOf(frame, MAGIC.length), MAGIC)) {
            throw new DriverException("Invalid XGT-lab magic");
        }
        if (frame[12] != expectedCommand) {
            throw new DriverException("Unexpected XGT-lab command in response");
        }
        if (frame[13] != point.deviceType().code()) {
            throw new DriverException("Unexpected XGT-lab device type in response");
        }
    }

    private byte[] transact(byte[] request) throws DriverException {
        try (Socket socket = new Socket()) {
            socket.connect(new InetSocketAddress(host, port), timeoutMs);
            socket.setSoTimeout(timeoutMs);
            OutputStream out = socket.getOutputStream();
            InputStream in = socket.getInputStream();
            out.write(request);
            out.flush();

            byte[] header = in.readNBytes(HEADER_LEN);
            if (header.length < HEADER_LEN) {
                throw new IOException("Incomplete XGT-lab header");
            }
            int count = (header[18] & 0xFF) | ((header[19] & 0xFF) << 8);
            byte command = header[12];
            int payloadLen = command == CMD_READ ? count * 2 : 0;
            byte[] payload = payloadLen > 0 ? in.readNBytes(payloadLen) : new byte[0];
            if (payload.length < payloadLen) {
                throw new IOException("Truncated XGT-lab payload");
            }
            byte[] response = new byte[HEADER_LEN + payload.length];
            System.arraycopy(header, 0, response, 0, HEADER_LEN);
            System.arraycopy(payload, 0, response, HEADER_LEN, payload.length);
            return response;
        } catch (IOException e) {
            throw new DriverException("LS XGT-lab I/O failed for " + host + ":" + port, e);
        }
    }

    private static long extractNumeric(DataRecord value) {
        if (value == null || value.rowCount() == 0) {
            throw new IllegalArgumentException("LS XGT write requires a value");
        }
        Map<String, Object> row = value.firstRow();
        for (String key : List.of("raw", "value")) {
            Object candidate = row.get(key);
            if (candidate instanceof Number number) {
                return number.longValue();
            }
            if (candidate != null) {
                String text = String.valueOf(candidate);
                int comma = text.indexOf(',');
                return Long.parseLong(comma < 0 ? text.trim() : text.substring(0, comma).trim());
            }
        }
        throw new IllegalArgumentException("LS XGT write requires numeric raw/value field");
    }
}
