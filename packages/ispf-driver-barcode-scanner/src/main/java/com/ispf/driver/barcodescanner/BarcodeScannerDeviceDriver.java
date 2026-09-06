package com.ispf.driver.barcodescanner;

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
import java.util.concurrent.atomic.AtomicReference;

/**
 * Barcode / QR scanner driver — newline-delimited ASCII scans over a raw TCP socket.
 * <p>
 * Point mapping is a logical channel name ({@code last}, {@code scan}, or any id).
 * {@code readPoints} returns the most recent complete scan line for each point.
 * {@code writePoint} sends a lab trigger / ACK command built from the mapping
 * ({@code TRIGGER}, {@code BEEP}) or the record {@code value} field when present.
 * <p>
 * Clean-room ISPF code, Apache-2.0 — JDK sockets only; not a vendor SDK wrapper.
 */
public class BarcodeScannerDeviceDriver implements DeviceDriver {

    private static final DataSchema VALUE_SCHEMA = DataSchema.builder("barcodeValue")
            .field("value", FieldType.STRING)
            .field("channel", FieldType.STRING)
            .build();

    private static final DriverMetadata METADATA = new DriverMetadata(
            "barcode-scanner",
            "Barcode scanner Driver",
            "0.1.0",
            "TCP newline barcode/QR scanner: last-scan reads, TRIGGER/BEEP writes",
            "ISPF",
            Map.of(
                    "host", "127.0.0.1",
                    "port", "9001",
                    "timeoutMs", "3000"
            ),
            null,
            Set.of("read", "write")
    );

    private DriverObject driverObject;
    private String host = "127.0.0.1";
    private int port = 9001;
    private int timeoutMs = 3000;

    private Socket socket;
    private InputStream in;
    private OutputStream out;
    private Thread reader;
    private volatile boolean connected;
    private volatile boolean running;

    private final AtomicReference<String> lastScan = new AtomicReference<>("");
    private final Map<String, String> channels = new ConcurrentHashMap<>();
    private final Object writeLock = new Object();

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
        disconnect();
        try {
            socket = new Socket();
            socket.connect(new InetSocketAddress(host, port), timeoutMs);
            socket.setTcpNoDelay(true);
            socket.setSoTimeout(0);
            in = socket.getInputStream();
            out = socket.getOutputStream();
            running = true;
            connected = true;
            reader = new Thread(this::readLoop, "barcode-scanner-reader");
            reader.setDaemon(true);
            reader.start();
            driverObject.log(DriverLogLevel.INFO, "Barcode scanner connected to " + host + ":" + port);
        } catch (IOException e) {
            disconnect();
            throw new DriverException("Barcode scanner connect failed for " + host + ":" + port, e);
        }
    }

    @Override
    public void disconnect() {
        connected = false;
        running = false;
        channels.clear();
        closeQuietly(socket);
        socket = null;
        in = null;
        out = null;
        if (reader != null) {
            try {
                reader.join(500);
            } catch (InterruptedException ignored) {
                Thread.currentThread().interrupt();
            }
            reader = null;
        }
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
            String scan = lastScan.get();
            driverObject.updateVariable(pointId, DataRecord.single(VALUE_SCHEMA, Map.of(
                    "value", scan == null ? "" : scan,
                    "channel", channel
            )));
        }
    }

    @Override
    public void writePoint(String pointId, DataRecord value) throws DriverException {
        ensureConnected();
        String channel = channels.getOrDefault(pointId, pointId);
        String command = resolveWriteCommand(channel, value);
        synchronized (writeLock) {
            try {
                writeLine(out, command);
            } catch (IOException e) {
                throw new DriverException("Barcode scanner write failed (" + command + ")", e);
            }
        }
    }

    private static String resolveWriteCommand(String channel, DataRecord value) {
        Object raw = value == null ? null : value.firstRow().get("value");
        if (raw != null && !String.valueOf(raw).isBlank()) {
            return String.valueOf(raw).trim();
        }
        String upper = channel.toUpperCase(Locale.ROOT);
        if (upper.contains("BEEP")) {
            return "BEEP";
        }
        return "TRIGGER";
    }

    private void readLoop() {
        try {
            while (running && in != null) {
                String line = readLine(in);
                if (line == null) {
                    break;
                }
                if (!line.isBlank()) {
                    lastScan.set(line.trim());
                    for (String pointId : channels.keySet()) {
                        driverObject.updateVariable(pointId, DataRecord.single(VALUE_SCHEMA, Map.of(
                                "value", line.trim(),
                                "channel", channels.getOrDefault(pointId, pointId)
                        )));
                    }
                }
            }
        } catch (IOException e) {
            if (running) {
                driverObject.log(DriverLogLevel.WARNING, "Barcode scanner reader stopped: " + e.getMessage());
            }
        } finally {
            connected = false;
        }
    }

    private void ensureConnected() throws DriverException {
        if (!isConnected()) {
            throw new DriverException("Not connected");
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

    private static void closeQuietly(Socket socket) {
        if (socket != null) {
            try {
                socket.close();
            } catch (IOException ignored) {
                // best-effort
            }
        }
    }
}
