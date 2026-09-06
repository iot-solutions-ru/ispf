package com.ispf.driver.gesrtp;

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
import java.util.stream.Collectors;
import java.util.stream.IntStream;

/**
 * Emerson/GE Fanuc SRTP driver — SRTP-lab MAILBOX subset over TCP.
 * <p>
 * Default port {@code 18245}. Point mapping examples: {@code %R100}, {@code R100},
 * {@code %AI1}, {@code %AQ2}, {@code %I10}, {@code %Q5} (optional {@code :count}).
 * Optional write maps {@code value}/{@code raw} to a single-word write at the point address.
 * <p>
 * <strong>Honesty:</strong> this is an ISPF clean-room SRTP-lab subset (Apache-2.0), not a full
 * CPE/SRTP stack. No session negotiation, no multi-segment transfers, no symbolic names, no
 * PLC control services. JDK sockets only — no PLC4X, no vendor SDKs.
 */
public class GeSrtpDeviceDriver implements DeviceDriver {

    private static final DataSchema VALUE_SCHEMA = DataSchema.builder("geSrtpValue")
            .field("value", FieldType.STRING)
            .field("memory", FieldType.STRING)
            .field("address", FieldType.INTEGER)
            .field("count", FieldType.INTEGER)
            .build();

    private static final DriverMetadata METADATA = new DriverMetadata(
            "ge-srtp",
            "GE SRTP Driver",
            "0.1.0",
            "SRTP-lab MAILBOX read/write for %R/%AI/%AQ/%I/%Q over TCP (not full CPE/SRTP)",
            "ISPF",
            Map.of(
                    "host", "127.0.0.1",
                    "port", "18245",
                    "timeoutMs", "3000"
            ),
            null,
            Set.of("read", "write")
    );

    private DriverObject driverObject;
    private String host = "127.0.0.1";
    private int port = 18245;
    private int timeoutMs = 3000;
    private final Map<String, GeSrtpPoint> points = new ConcurrentHashMap<>();
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
        driverObject.log(DriverLogLevel.INFO, "GE SRTP-lab ready for " + host + ":" + port);
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
            GeSrtpPoint point = GeSrtpPoint.parse(entry.getValue());
            points.put(entry.getKey(), point);
            driverObject.updateVariable(entry.getKey(), readMemory(point));
        }
    }

    @Override
    public void writePoint(String pointId, DataRecord value) throws DriverException {
        if (!isConnected()) {
            throw new DriverException("Not connected");
        }
        GeSrtpPoint point = points.get(pointId);
        if (point == null) {
            throw new DriverException("Unknown point: " + pointId);
        }
        int word = (int) extractNumeric(value) & 0xFFFF;
        GeSrtpPoint single = new GeSrtpPoint(point.memoryType(), point.address(), 1);
        exchange(GeSrtpFrame.CMD_WRITE, single, new int[] { word });
        driverObject.updateVariable(pointId, DataRecord.single(VALUE_SCHEMA, Map.of(
                "value", String.valueOf(word),
                "memory", single.deviceLabel(),
                "address", single.address(),
                "count", 1
        )));
    }

    private DataRecord readMemory(GeSrtpPoint point) throws DriverException {
        int[] words = exchange(GeSrtpFrame.CMD_READ, point, null);
        String joined = IntStream.of(words).mapToObj(String::valueOf).collect(Collectors.joining(","));
        return DataRecord.single(VALUE_SCHEMA, Map.of(
                "value", joined,
                "memory", point.deviceLabel(),
                "address", point.address(),
                "count", point.count()
        ));
    }

    private int[] exchange(byte command, GeSrtpPoint point, int[] writeWords) throws DriverException {
        byte[] request = GeSrtpFrame.buildRequest(command, point, writeWords);
        try (Socket socket = new Socket()) {
            socket.connect(new InetSocketAddress(host, port), timeoutMs);
            socket.setSoTimeout(timeoutMs);
            OutputStream out = socket.getOutputStream();
            InputStream in = socket.getInputStream();
            out.write(request);
            out.flush();

            byte[] lengthHeader = in.readNBytes(2);
            if (lengthHeader.length < 2) {
                throw new IOException("Incomplete SRTP-lab response length");
            }
            int length = ((lengthHeader[0] & 0xFF) << 8) | (lengthHeader[1] & 0xFF);
            byte[] body = in.readNBytes(length);
            if (body.length < length) {
                throw new IOException("Truncated SRTP-lab response");
            }
            GeSrtpFrame.ParsedResponse parsed = GeSrtpFrame.parseResponse(lengthHeader, body);
            if (parsed.status() != GeSrtpFrame.STATUS_OK) {
                throw new DriverException("SRTP-lab status 0x" + Integer.toHexString(parsed.status() & 0xFF));
            }
            if (command == GeSrtpFrame.CMD_READ && parsed.words().length < point.count()) {
                throw new DriverException("SRTP-lab short read");
            }
            return parsed.words();
        } catch (IOException e) {
            throw new DriverException("GE SRTP-lab I/O failed for " + host + ":" + port, e);
        }
    }

    private static long extractNumeric(DataRecord value) {
        if (value == null || value.rowCount() == 0) {
            throw new IllegalArgumentException("GE SRTP-lab write requires a value");
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
        throw new IllegalArgumentException("GE SRTP-lab write requires numeric raw/value field");
    }
}
