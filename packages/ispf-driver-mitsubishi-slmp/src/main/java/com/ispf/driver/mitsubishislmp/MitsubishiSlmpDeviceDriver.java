package com.ispf.driver.mitsubishislmp;

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
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Mitsubishi SLMP driver — 3E binary device-read/write for D registers over TCP.
 * <p>
 * Point mapping: {@code D100}, {@code D:100}, or {@code D:100:1}. Write maps the record
 * {@code value}/{@code raw} field to a single-word device write (command 0x1401).
 * Clean-room ISPF code, Apache-2.0 — no proprietary MELSOFT stack.
 */
public class MitsubishiSlmpDeviceDriver implements DeviceDriver {

    private static final short CMD_DEVICE_READ = 0x0401;
    private static final short CMD_DEVICE_WRITE = 0x1401;
    private static final short SUBCOMMAND = 0x0000;

    private static final DataSchema VALUE_SCHEMA = DataSchema.builder("slmpValue")
            .field("value", FieldType.STRING)
            .field("device", FieldType.STRING)
            .field("address", FieldType.INTEGER)
            .field("count", FieldType.INTEGER)
            .build();

    private static final DriverMetadata METADATA = new DriverMetadata(
            "mitsubishi-slmp",
            "Mitsubishi SLMP Driver",
            "0.1.0",
            "Reads/writes Mitsubishi PLC D registers via SLMP 3E binary over TCP",
            "ISPF",
            Map.of(
                    "host", "127.0.0.1",
                    "port", "5007",
                    "timeoutMs", "3000",
                    "networkNo", "0",
                    "pcNo", "255",
                    "ioNo", "1023",
                    "stationNo", "0",
                    "monitoringTimer", "16"
            ),
            null,
            Set.of("read", "write")
    );

    private DriverObject driverObject;
    private String host = "127.0.0.1";
    private int port = 5007;
    private int timeoutMs = 3000;
    private int networkNo = 0;
    private int pcNo = 0xFF;
    private int ioNo = 0x03FF;
    private int stationNo = 0;
    private int monitoringTimer = 16;
    private final Map<String, MitsubishiSlmpPoint> points = new ConcurrentHashMap<>();
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
            case "networkNo" -> networkNo = Integer.parseInt(value.trim());
            case "pcNo" -> pcNo = Integer.parseInt(value.trim());
            case "ioNo" -> ioNo = Integer.parseInt(value.trim());
            case "stationNo" -> stationNo = Integer.parseInt(value.trim());
            case "monitoringTimer" -> monitoringTimer = Integer.parseInt(value.trim());
            default -> { }
        }
    }

    @Override
    public void connect() throws DriverException {
        connected = true;
        driverObject.log(DriverLogLevel.INFO, "Mitsubishi SLMP ready for " + host + ":" + port);
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
            MitsubishiSlmpPoint point = MitsubishiSlmpPoint.parse(entry.getValue());
            points.put(entry.getKey(), point);
            driverObject.updateVariable(entry.getKey(), readDevice(point));
        }
    }

    @Override
    public void writePoint(String pointId, DataRecord value) throws DriverException {
        if (!isConnected()) {
            throw new DriverException("Not connected");
        }
        MitsubishiSlmpPoint point = points.get(pointId);
        if (point == null) {
            throw new DriverException("Unknown point: " + pointId);
        }
        int word = (int) extractNumeric(value) & 0xFFFF;
        writeDevice(point, word);
        driverObject.updateVariable(pointId, DataRecord.single(VALUE_SCHEMA, Map.of(
                "value", String.valueOf(word),
                "device", point.deviceCode(),
                "address", point.address(),
                "count", 1
        )));
    }

    private DataRecord readDevice(MitsubishiSlmpPoint point) throws DriverException {
        byte[] requestBody = buildAccessBody(CMD_DEVICE_READ, point, null);
        byte[] response = transact(requestBody);
        int[] words = decodeWords(response, point.count());
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < words.length; i++) {
            if (i > 0) {
                sb.append(',');
            }
            sb.append(words[i]);
        }
        return DataRecord.single(VALUE_SCHEMA, Map.of(
                "value", sb.toString(),
                "device", point.deviceCode(),
                "address", point.address(),
                "count", point.count()
        ));
    }

    private void writeDevice(MitsubishiSlmpPoint point, int word) throws DriverException {
        MitsubishiSlmpPoint single = new MitsubishiSlmpPoint(point.deviceCode(), point.address(), 1);
        byte[] requestBody = buildAccessBody(CMD_DEVICE_WRITE, single, new int[] { word });
        decodeWords(transact(requestBody), 0);
    }

    private byte[] buildAccessBody(short command, MitsubishiSlmpPoint point, int[] writeWords) {
        int dataWords = writeWords == null ? 0 : writeWords.length;
        ByteBuffer body = ByteBuffer.allocate(10 + dataWords * 2).order(ByteOrder.LITTLE_ENDIAN);
        body.putShort((short) monitoringTimer);
        body.putShort(command);
        body.putShort(SUBCOMMAND);
        body.put((byte) (point.address() & 0xFF));
        body.put((byte) ((point.address() >> 8) & 0xFF));
        body.put((byte) ((point.address() >> 16) & 0xFF));
        body.put(point.binaryDeviceCode());
        body.putShort((short) point.count());
        if (writeWords != null) {
            for (int word : writeWords) {
                body.putShort((short) (word & 0xFFFF));
            }
        }
        return body.array();
    }

    private byte[] transact(byte[] requestBody) throws DriverException {
        try (Socket socket = new Socket()) {
            socket.connect(new InetSocketAddress(host, port), timeoutMs);
            socket.setSoTimeout(timeoutMs);
            OutputStream out = socket.getOutputStream();
            InputStream in = socket.getInputStream();
            out.write(wrapRequest(requestBody));
            out.flush();
            return readResponse(in);
        } catch (IOException e) {
            throw new DriverException("Mitsubishi SLMP I/O failed for " + host + ":" + port, e);
        }
    }

    private byte[] wrapRequest(byte[] body) {
        ByteBuffer frame = ByteBuffer.allocate(11 + body.length).order(ByteOrder.LITTLE_ENDIAN);
        frame.put((byte) 0x50);
        frame.put((byte) 0x00);
        frame.put((byte) (networkNo & 0xFF));
        frame.put((byte) (pcNo & 0xFF));
        frame.putShort((short) (ioNo & 0xFFFF));
        frame.put((byte) (stationNo & 0xFF));
        frame.putShort((short) body.length);
        frame.put(body);
        return frame.array();
    }

    private static byte[] readResponse(InputStream in) throws IOException {
        byte[] header = in.readNBytes(9);
        if (header.length < 9) {
            throw new IOException("Incomplete SLMP response header");
        }
        if ((header[0] & 0xFF) != 0xD0 || header[1] != 0x00) {
            throw new IOException("Unexpected SLMP subheader");
        }
        int length = (header[7] & 0xFF) | ((header[8] & 0xFF) << 8);
        byte[] payload = in.readNBytes(length);
        if (payload.length < length) {
            throw new IOException("Truncated SLMP response");
        }
        return payload;
    }

    private static int[] decodeWords(byte[] payload, int expectedCount) throws DriverException {
        if (payload.length < 2) {
            throw new DriverException("SLMP response too short");
        }
        int endCode = (payload[0] & 0xFF) | ((payload[1] & 0xFF) << 8);
        if (endCode != 0) {
            throw new DriverException("SLMP end code 0x" + Integer.toHexString(endCode));
        }
        int[] words = new int[expectedCount];
        for (int i = 0; i < expectedCount; i++) {
            int offset = 2 + i * 2;
            if (offset + 1 >= payload.length) {
                words[i] = 0;
            } else {
                words[i] = (payload[offset] & 0xFF) | ((payload[offset + 1] & 0xFF) << 8);
            }
        }
        return words;
    }

    private static long extractNumeric(DataRecord value) {
        if (value == null || value.rowCount() == 0) {
            throw new IllegalArgumentException("SLMP write requires a value");
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
        throw new IllegalArgumentException("SLMP write requires numeric raw/value field");
    }
}
