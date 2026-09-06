package com.ispf.driver.mitsubishimelsec;

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
 * Mitsubishi MELSEC driver — MC Protocol / SLMP-compatible 3E binary device access over TCP.
 * <p>
 * Point mapping: {@code D100} or {@code D100:2}. Optional write maps {@code value}/{@code raw}
 * to a single-word batch write. Limitations: D registers only; no bit devices, no ASCII 1E/3E
 * frames, no multi-block random access. Clean-room ISPF code, Apache-2.0 — no MELSOFT / PLC4X.
 */
public class MitsubishiMelsecDeviceDriver implements DeviceDriver {

    private static final DataSchema VALUE_SCHEMA = DataSchema.builder("melsecValue")
            .field("value", FieldType.STRING)
            .field("register", FieldType.STRING)
            .field("address", FieldType.INTEGER)
            .field("words", FieldType.INTEGER)
            .build();

    private static final DriverMetadata METADATA = new DriverMetadata(
            "mitsubishi-melsec",
            "Mitsubishi MELSEC Driver",
            "0.1.0",
            "Reads/writes MELSEC D registers via MC Protocol 3E binary (SLMP-compatible) over TCP",
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
    private final Map<String, MitsubishiMelsecPoint> points = new ConcurrentHashMap<>();
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
        driverObject.log(DriverLogLevel.INFO, "Mitsubishi MELSEC ready for " + host + ":" + port);
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
            MitsubishiMelsecPoint point = MitsubishiMelsecPoint.parse(entry.getValue());
            points.put(entry.getKey(), point);
            driverObject.updateVariable(entry.getKey(), readRegisters(point));
        }
    }

    @Override
    public void writePoint(String pointId, DataRecord value) throws DriverException {
        if (!isConnected()) {
            throw new DriverException("Not connected");
        }
        MitsubishiMelsecPoint point = points.get(pointId);
        if (point == null) {
            throw new DriverException("Unknown point: " + pointId);
        }
        int word = (int) extractNumeric(value) & 0xFFFF;
        MitsubishiMelsecPoint single = new MitsubishiMelsecPoint(point.address(), 1);
        exchange(MitsubishiMelsecFrame.CMD_BATCH_WRITE, single, new int[] { word }, 0);
        driverObject.updateVariable(pointId, DataRecord.single(VALUE_SCHEMA, Map.of(
                "value", String.valueOf(word),
                "register", single.deviceLabel(),
                "address", single.address(),
                "words", 1
        )));
    }

    private DataRecord readRegisters(MitsubishiMelsecPoint point) throws DriverException {
        int[] words = exchange(MitsubishiMelsecFrame.CMD_BATCH_READ, point, null, point.wordCount());
        String joined = IntStream.of(words).mapToObj(String::valueOf).collect(Collectors.joining(","));
        return DataRecord.single(VALUE_SCHEMA, Map.of(
                "value", joined,
                "register", point.deviceLabel(),
                "address", point.address(),
                "words", point.wordCount()
        ));
    }

    private int[] exchange(int command, MitsubishiMelsecPoint point, int[] writeWords, int expectedWords)
            throws DriverException {
        byte[] request = MitsubishiMelsecFrame.buildRequest(
                networkNo, pcNo, ioNo, stationNo, monitoringTimer, command, point, writeWords
        );
        try (Socket socket = new Socket()) {
            socket.connect(new InetSocketAddress(host, port), timeoutMs);
            socket.setSoTimeout(timeoutMs);
            OutputStream out = socket.getOutputStream();
            InputStream in = socket.getInputStream();
            out.write(request);
            out.flush();

            byte[] header = in.readNBytes(9);
            if (header.length < 9) {
                throw new IOException("Incomplete MELSEC response header");
            }
            int length = (header[7] & 0xFF) | ((header[8] & 0xFF) << 8);
            byte[] payload = in.readNBytes(length);
            if (payload.length < length) {
                throw new IOException("Truncated MELSEC response");
            }
            MitsubishiMelsecFrame.ParsedResponse parsed = MitsubishiMelsecFrame.parseResponse(header, payload);
            if (parsed.endCode() != 0) {
                throw new DriverException("MELSEC end code 0x" + Integer.toHexString(parsed.endCode()));
            }
            return MitsubishiMelsecFrame.extractWords(parsed.payload(), expectedWords);
        } catch (IOException e) {
            throw new DriverException("Mitsubishi MELSEC I/O failed for " + host + ":" + port, e);
        }
    }

    private static long extractNumeric(DataRecord value) {
        if (value == null || value.rowCount() == 0) {
            throw new IllegalArgumentException("MELSEC write requires a value");
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
        throw new IllegalArgumentException("MELSEC write requires numeric raw/value field");
    }
}
