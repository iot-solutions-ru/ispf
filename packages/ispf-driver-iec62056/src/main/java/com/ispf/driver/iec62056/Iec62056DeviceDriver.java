package com.ispf.driver.iec62056;

import com.ispf.core.model.DataRecord;
import com.ispf.core.model.DataSchema;
import com.ispf.core.model.FieldType;
import com.ispf.driver.DeviceDriver;
import com.ispf.driver.DriverException;
import com.ispf.driver.DriverMetadata;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * IEC 62056-21 Mode C optical/TCP ASCII readout companion — not a DLMS/COSEM APDU stack
 * (see the separate {@code dlms} pack for that).
 * <p>
 * Implements the Mode C sign-on / identification / data-readout subset over TCP:
 * sign-on {@code /?!}, identification line, ACK {@code 0Z0}, then data lines
 * {@code OBIS(value*unit)} until {@code !}. Point mapping is an OBIS code
 * ({@code 1.8.0}, {@code 1-0:1.8.0}). Clean-room ISPF code, Apache-2.0 —
 * no proprietary meter stacks.
 */
public class Iec62056DeviceDriver implements DeviceDriver {

    private static final byte ACK = 0x06;
    private static final byte STX = 0x02;
    private static final byte ETX = 0x03;
    private static final byte CR = 0x0D;
    private static final byte LF = 0x0A;

    private static final Pattern DATA_LINE = Pattern.compile(
            "^([^()]+)\\(([^)*]*)(?:\\*([^)]*))?\\)\\s*$"
    );

    private static final DataSchema VALUE_SCHEMA = DataSchema.builder("iec62056Value")
            .field("value", FieldType.STRING)
            .field("unit", FieldType.STRING)
            .field("obis", FieldType.STRING)
            .field("identification", FieldType.STRING)
            .build();

    private static final DriverMetadata METADATA = new DriverMetadata(
            "iec62056",
            "IEC 62056-21 Mode C Driver",
            "0.1.0",
            "IEC 62056-21 Mode C ASCII readout over TCP (sign-on /?! , identification, OBIS data lines);"
                    + " companion to the separate DLMS/COSEM pack — not a full APDU stack",
            "ISPF",
            Map.of(
                    "host", "127.0.0.1",
                    "port", "4059",
                    "timeoutMs", "3000",
                    "deviceAddress", "",
                    "baudId", "5"
            ),
            null,
            Set.of("read")
    );

    private DriverObject driverObject;
    private String host = "127.0.0.1";
    private int port = 4059;
    private int timeoutMs = 3000;
    private String deviceAddress = "";
    private char baudId = '5';
    private final Map<String, Iec62056Point> points = new ConcurrentHashMap<>();
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
            case "deviceAddress" -> deviceAddress = value.trim();
            case "baudId" -> {
                String trimmed = value.trim();
                if (trimmed.length() != 1 || trimmed.charAt(0) < '0' || trimmed.charAt(0) > '6') {
                    throw new IllegalArgumentException("baudId must be a single digit 0-6");
                }
                baudId = trimmed.charAt(0);
            }
            default -> { }
        }
    }

    @Override
    public void connect() throws DriverException {
        connected = true;
        driverObject.log(DriverLogLevel.INFO,
                "IEC 62056-21 Mode C ready for " + host + ":" + port);
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
        ReadoutSession session = performReadout();
        for (Map.Entry<String, String> entry : pointMappings.entrySet()) {
            Iec62056Point point = Iec62056Point.parse(entry.getValue());
            points.put(entry.getKey(), point);
            driverObject.updateVariable(entry.getKey(), toRecord(point, session));
        }
    }

    @Override
    public void writePoint(String pointId, DataRecord value) throws DriverException {
        throw new DriverException("IEC 62056-21 Mode C driver is readout-only in v0.1");
    }

    private DataRecord toRecord(Iec62056Point point, ReadoutSession session) throws DriverException {
        DataLine match = null;
        for (DataLine line : session.lines().values()) {
            if (point.matchesLineObis(line.obis())) {
                match = line;
                break;
            }
        }
        if (match == null) {
            throw new DriverException("OBIS " + point.obis() + " not present in Mode C readout");
        }
        return DataRecord.single(VALUE_SCHEMA, Map.of(
                "value", match.value(),
                "unit", match.unit(),
                "obis", match.obis(),
                "identification", session.identification()
        ));
    }

    private ReadoutSession performReadout() throws DriverException {
        try (Socket socket = new Socket()) {
            socket.connect(new InetSocketAddress(host, port), timeoutMs);
            socket.setSoTimeout(timeoutMs);
            OutputStream out = socket.getOutputStream();
            InputStream in = socket.getInputStream();

            out.write(buildSignOn());
            out.flush();

            String identification = readLineAscii(in);
            if (identification.isEmpty() || identification.charAt(0) != '/') {
                throw new DriverException("Expected IEC 62056-21 identification, got: " + identification);
            }

            out.write(new byte[] { ACK, '0', (byte) baudId, '0', CR, LF });
            out.flush();

            Map<String, DataLine> lines = readDataBlock(in);
            return new ReadoutSession(identification, lines);
        } catch (IOException e) {
            throw new DriverException("IEC 62056-21 I/O failed for " + host + ":" + port, e);
        }
    }

    private byte[] buildSignOn() {
        String address = deviceAddress == null ? "" : deviceAddress.trim();
        String signOn = "/?" + address + "!\r\n";
        return signOn.getBytes(StandardCharsets.US_ASCII);
    }

    private static Map<String, DataLine> readDataBlock(InputStream in) throws IOException, DriverException {
        int first = in.read();
        if (first < 0) {
            throw new DriverException("EOF before Mode C data block");
        }
        if (first != STX) {
            throw new DriverException("Expected STX before Mode C data, got 0x" + Integer.toHexString(first));
        }

        ByteArrayOutputStream block = new ByteArrayOutputStream();
        int b;
        while ((b = in.read()) >= 0) {
            block.write(b);
            if (b == ETX) {
                break;
            }
        }
        if (b != ETX) {
            throw new DriverException("Mode C data block missing ETX");
        }
        int bcc = in.read();
        if (bcc < 0) {
            throw new DriverException("Mode C data block missing BCC");
        }
        byte[] payload = block.toByteArray();
        byte expected = 0;
        for (byte value : payload) {
            expected ^= value;
        }
        if ((expected & 0xFF) != (bcc & 0xFF)) {
            throw new DriverException("Mode C BCC mismatch");
        }

        String text = new String(payload, 0, payload.length - 1, StandardCharsets.US_ASCII);
        Map<String, DataLine> lines = new LinkedHashMap<>();
        for (String rawLine : text.split("\\r\\n|\\n")) {
            String line = rawLine.trim();
            if (line.isEmpty() || "!".equals(line) || line.startsWith("!")) {
                continue;
            }
            Matcher matcher = DATA_LINE.matcher(line);
            if (!matcher.matches()) {
                continue;
            }
            String obis = matcher.group(1).trim();
            String value = matcher.group(2) == null ? "" : matcher.group(2).trim();
            String unit = matcher.group(3) == null ? "" : matcher.group(3).trim();
            lines.put(obis, new DataLine(obis, value, unit));
        }
        return lines;
    }

    private static String readLineAscii(InputStream in) throws IOException {
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        int prev = -1;
        int b;
        while ((b = in.read()) >= 0) {
            if (b == LF && prev == CR) {
                break;
            }
            if (b != CR && b != LF) {
                buffer.write(b);
            }
            prev = b;
        }
        return buffer.toString(StandardCharsets.US_ASCII);
    }

    private record DataLine(String obis, String value, String unit) { }

    private record ReadoutSession(String identification, Map<String, DataLine> lines) { }
}
