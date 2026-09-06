package com.ispf.driver.weighbridge;

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
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Weighbridge / truck-scale driver — ASCII continuous or polled weight lines over TCP.
 * <p>
 * Point mapping selects the channel ({@code weight}, {@code gross}, {@code net}).
 * {@code readPoints} sends {@code W} (print/poll) and parses a scale line such as
 * {@code ST,GS,+000123.4kg} or {@code 123.4 kg} into record fields {@code value},
 * {@code unit}, {@code status}, {@code raw}.
 * {@code writePoint} sends lab commands {@code ZERO}, {@code TARE}, or the record {@code value}.
 * <p>
 * Clean-room ISPF lab dialect, Apache-2.0 — JDK sockets only; not a vendor scale SDK.
 */
public class WeighbridgeDeviceDriver implements DeviceDriver {

    private static final Pattern SIGNED_WEIGHT = Pattern.compile("([+-]?\\d+(?:\\.\\d+)?)\\s*([a-zA-Z]+)?");

    private static final DataSchema VALUE_SCHEMA = DataSchema.builder("weighbridgeValue")
            .field("value", FieldType.STRING)
            .field("unit", FieldType.STRING)
            .field("status", FieldType.STRING)
            .field("raw", FieldType.STRING)
            .field("channel", FieldType.STRING)
            .build();

    private static final DriverMetadata METADATA = new DriverMetadata(
            "weighbridge",
            "Weighbridge Driver",
            "0.1.0",
            "TCP ASCII weighbridge: W poll for weight, ZERO/TARE writes",
            "ISPF",
            Map.of(
                    "host", "127.0.0.1",
                    "port", "4001",
                    "timeoutMs", "3000"
            ),
            null,
            Set.of("read", "write")
    );

    private DriverObject driverObject;
    private String host = "127.0.0.1";
    private int port = 4001;
    private int timeoutMs = 3000;
    private Socket socket;
    private final Map<String, String> channels = new ConcurrentHashMap<>();
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
        try {
            Socket next = new Socket();
            next.connect(new InetSocketAddress(host, port), timeoutMs);
            next.setSoTimeout(timeoutMs);
            next.setTcpNoDelay(true);
            socket = next;
            connected = true;
            driverObject.log(DriverLogLevel.INFO, "Weighbridge connected to " + host + ":" + port);
        } catch (IOException e) {
            closeSocket();
            throw new DriverException("Weighbridge connect failed for " + host + ":" + port, e);
        }
    }

    @Override
    public void disconnect() {
        connected = false;
        channels.clear();
        closeSocket();
    }

    @Override
    public boolean isConnected() {
        return connected && socket != null && socket.isConnected() && !socket.isClosed();
    }

    @Override
    public void readPoints(Map<String, String> pointMappings) throws DriverException {
        ensureConnected();
        for (Map.Entry<String, String> entry : pointMappings.entrySet()) {
            String pointId = entry.getKey();
            String channel = entry.getValue() == null || entry.getValue().isBlank()
                    ? pointId
                    : entry.getValue().trim();
            channels.put(pointId, channel);
            String raw = pollWeight();
            ParsedWeight parsed = parseWeight(raw);
            driverObject.updateVariable(pointId, DataRecord.single(VALUE_SCHEMA, Map.of(
                    "value", parsed.value,
                    "unit", parsed.unit,
                    "status", parsed.status,
                    "raw", raw,
                    "channel", channel
            )));
        }
    }

    @Override
    public void writePoint(String pointId, DataRecord value) throws DriverException {
        ensureConnected();
        String channel = channels.getOrDefault(pointId, pointId);
        String command = resolveCommand(channel, value);
        synchronized (this) {
            try {
                writeLine(socket.getOutputStream(), command);
                // Many scales ACK with a line; drain one optional response without failing.
                try {
                    socket.setSoTimeout(Math.min(500, timeoutMs));
                    readLine(socket.getInputStream());
                } catch (IOException ignored) {
                    // no ACK is fine for ZERO/TARE lab dialect
                } finally {
                    socket.setSoTimeout(timeoutMs);
                }
            } catch (IOException e) {
                throw new DriverException("Weighbridge write failed (" + command + ")", e);
            }
        }
    }

    private synchronized String pollWeight() throws DriverException {
        try {
            writeLine(socket.getOutputStream(), "W");
            String line = readLine(socket.getInputStream());
            if (line == null) {
                throw new IOException("EOF while reading weight");
            }
            return line.trim();
        } catch (IOException e) {
            throw new DriverException("Weighbridge poll failed for " + host + ":" + port, e);
        }
    }

    private static String resolveCommand(String channel, DataRecord value) {
        Object raw = value == null ? null : value.firstRow().get("value");
        if (raw != null && !String.valueOf(raw).isBlank()) {
            return String.valueOf(raw).trim().toUpperCase(Locale.ROOT);
        }
        String upper = channel.toUpperCase(Locale.ROOT);
        if (upper.contains("TARE")) {
            return "TARE";
        }
        if (upper.contains("ZERO")) {
            return "ZERO";
        }
        return "ZERO";
    }

    static ParsedWeight parseWeight(String raw) {
        if (raw == null || raw.isBlank()) {
            return new ParsedWeight("", "", "", "");
        }
        String status = "";
        String upper = raw.toUpperCase(Locale.ROOT);
        if (upper.startsWith("ST")) {
            status = "ST";
        } else if (upper.startsWith("US")) {
            status = "US";
        } else if (upper.startsWith("OL")) {
            status = "OL";
        }
        Matcher matcher = SIGNED_WEIGHT.matcher(raw.replace(',', ' '));
        if (matcher.find()) {
            String numeric = matcher.group(1);
            if (numeric.startsWith("+")) {
                numeric = numeric.substring(1);
            }
            String unit = matcher.group(2) == null ? "kg" : matcher.group(2);
            return new ParsedWeight(numeric, unit, status, raw);
        }
        return new ParsedWeight(raw.trim(), "", status, raw);
    }

    private void ensureConnected() throws DriverException {
        if (!isConnected()) {
            throw new DriverException("Not connected");
        }
    }

    private void closeSocket() {
        Socket current = socket;
        socket = null;
        if (current != null) {
            try {
                current.close();
            } catch (IOException ignored) {
                // best-effort
            }
        }
    }

    static void writeLine(OutputStream out, String line) throws IOException {
        out.write((line + "\r\n").getBytes(StandardCharsets.US_ASCII));
        out.flush();
    }

    static String readLine(InputStream in) throws IOException {
        ByteArrayOutputStream buf = new ByteArrayOutputStream();
        while (true) {
            int b = in.read();
            if (b < 0) {
                if (buf.size() == 0) {
                    return null;
                }
                break;
            }
            if (b == '\n') {
                break;
            }
            if (b != '\r') {
                buf.write(b);
            }
        }
        return buf.toString(StandardCharsets.US_ASCII);
    }

    record ParsedWeight(String value, String unit, String status, String raw) {
    }
}
