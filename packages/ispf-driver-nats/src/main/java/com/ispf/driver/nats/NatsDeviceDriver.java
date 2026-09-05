package com.ispf.driver.nats;

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
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.ReentrantLock;

/**
 * NATS driver — text protocol over TCP (INFO/CONNECT handshake, SUB, PUB, MSG, PING/PONG).
 * <p>
 * Point mapping is the NATS subject. {@code readPoints} SUBscribes and returns the last MSG
 * payload for that subject (waiting up to {@code timeoutMs}). {@code writePoint} PUBlishes the
 * record {@code value} field (or sole field) to the subject.
 * Clean-room ISPF code, Apache-2.0 — JDK {@link Socket} only.
 */
public class NatsDeviceDriver implements DeviceDriver {

    private static final DataSchema VALUE_SCHEMA = DataSchema.builder("natsValue")
            .field("value", FieldType.STRING)
            .field("subject", FieldType.STRING)
            .field("sid", FieldType.STRING)
            .build();

    private static final DriverMetadata METADATA = new DriverMetadata(
            "nats",
            "NATS Driver",
            "0.1.0",
            "NATS text protocol client: INFO/CONNECT, SUB for reads, PUB for writes",
            "ISPF",
            Map.of(
                    "host", "127.0.0.1",
                    "port", "4222",
                    "timeoutMs", "3000",
                    "clientName", "ispf-nats",
                    "pollIntervalMs", "5000"
            ),
            null,
            Set.of("read", "write")
    );

    private DriverObject driverObject;
    private String host = "127.0.0.1";
    private int port = 4222;
    private int timeoutMs = 3000;
    private String clientName = "ispf-nats";

    private Socket socket;
    private InputStream in;
    private OutputStream out;
    private Thread reader;
    private volatile boolean connected;
    private volatile boolean running;
    private volatile String serverInfo = "";

    private final AtomicInteger nextSid = new AtomicInteger(1);
    private final Map<String, String> subjectBySid = new ConcurrentHashMap<>();
    private final Map<String, String> sidBySubject = new ConcurrentHashMap<>();
    private final Map<String, String> lastPayloads = new ConcurrentHashMap<>();
    private final Map<String, String> pointSubjects = new ConcurrentHashMap<>();

    private final ReentrantLock lock = new ReentrantLock();
    private final Condition signal = lock.newCondition();
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
            case "clientName", "name" -> clientName = value.trim();
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
            reader = new Thread(this::readLoop, "nats-reader-" + clientName);
            reader.setDaemon(true);
            reader.start();

            awaitInfo(timeoutMs);
            String connect = "CONNECT {\"verbose\":false,\"pedantic\":false,\"lang\":\"java\","
                    + "\"version\":\"0.1.0\",\"protocol\":1,\"name\":\""
                    + escapeJson(clientName) + "\"}\r\n";
            writeRaw(connect.getBytes(StandardCharsets.UTF_8));
            connected = true;
            driverObject.log(DriverLogLevel.INFO, "NATS connected to " + host + ":" + port
                    + (serverInfo.isBlank() ? "" : " (" + serverInfo + ")"));
        } catch (DriverException e) {
            disconnect();
            throw e;
        } catch (IOException e) {
            disconnect();
            throw new DriverException("NATS connect failed for " + host + ":" + port, e);
        }
    }

    @Override
    public void disconnect() {
        connected = false;
        running = false;
        if (socket != null) {
            try {
                socket.close();
            } catch (IOException ignored) {
                // ignore
            }
        }
        if (reader != null) {
            try {
                reader.join(500);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            reader = null;
        }
        socket = null;
        in = null;
        out = null;
        subjectBySid.clear();
        sidBySubject.clear();
        lastPayloads.clear();
        pointSubjects.clear();
        serverInfo = "";
    }

    @Override
    public boolean isConnected() {
        return connected && socket != null && socket.isConnected() && !socket.isClosed();
    }

    @Override
    public void readPoints(Map<String, String> pointMappings) throws DriverException {
        if (!isConnected()) {
            throw new DriverException("Not connected");
        }
        pointSubjects.clear();
        for (Map.Entry<String, String> entry : pointMappings.entrySet()) {
            String pointId = entry.getKey();
            String subject = entry.getValue() == null || entry.getValue().isBlank()
                    ? pointId
                    : entry.getValue().trim();
            pointSubjects.put(pointId, subject);
            String sid = ensureSubscription(subject);
            String payload = awaitPayload(subject, timeoutMs);
            driverObject.updateVariable(pointId, DataRecord.single(VALUE_SCHEMA, Map.of(
                    "value", payload == null ? "" : payload,
                    "subject", subject,
                    "sid", sid
            )));
        }
    }

    @Override
    public void writePoint(String pointId, DataRecord value) throws DriverException {
        if (!isConnected()) {
            throw new DriverException("Not connected");
        }
        String subject = pointSubjects.getOrDefault(pointId, pointId);
        String payload = extractValue(value);
        publish(subject, payload);
        lastPayloads.put(subject, payload);
        String sid = sidBySubject.getOrDefault(subject, "");
        driverObject.updateVariable(pointId, DataRecord.single(VALUE_SCHEMA, Map.of(
                "value", payload,
                "subject", subject,
                "sid", sid
        )));
    }

    private String ensureSubscription(String subject) throws DriverException {
        String existing = sidBySubject.get(subject);
        if (existing != null) {
            return existing;
        }
        String sid = String.valueOf(nextSid.getAndIncrement());
        sidBySubject.put(subject, sid);
        subjectBySid.put(sid, subject);
        writeRaw(("SUB " + subject + " " + sid + "\r\n").getBytes(StandardCharsets.UTF_8));
        return sid;
    }

    private void publish(String subject, String payload) throws DriverException {
        byte[] data = payload.getBytes(StandardCharsets.UTF_8);
        String header = "PUB " + subject + " " + data.length + "\r\n";
        ByteArrayOutputStream buf = new ByteArrayOutputStream(header.length() + data.length + 2);
        buf.writeBytes(header.getBytes(StandardCharsets.US_ASCII));
        buf.writeBytes(data);
        buf.writeBytes("\r\n".getBytes(StandardCharsets.US_ASCII));
        writeRaw(buf.toByteArray());
    }

    private void writeRaw(byte[] bytes) throws DriverException {
        synchronized (writeLock) {
            try {
                if (out == null) {
                    throw new IOException("Not connected");
                }
                out.write(bytes);
                out.flush();
            } catch (IOException e) {
                throw new DriverException("NATS write failed", e);
            }
        }
    }

    private void awaitInfo(long waitMs) throws DriverException {
        long deadline = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(Math.max(waitMs, 1));
        lock.lock();
        try {
            while (serverInfo.isBlank()) {
                long remaining = deadline - System.nanoTime();
                if (remaining <= 0) {
                    throw new DriverException("NATS timeout waiting for INFO");
                }
                try {
                    signal.awaitNanos(remaining);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    throw new DriverException("Interrupted waiting for NATS INFO", e);
                }
            }
        } finally {
            lock.unlock();
        }
    }

    private String awaitPayload(String subject, long waitMs) throws DriverException {
        long deadline = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(Math.max(waitMs, 1));
        lock.lock();
        try {
            while (true) {
                String value = lastPayloads.get(subject);
                if (value != null) {
                    return value;
                }
                long remaining = deadline - System.nanoTime();
                if (remaining <= 0) {
                    return lastPayloads.getOrDefault(subject, "");
                }
                try {
                    signal.awaitNanos(remaining);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    throw new DriverException("Interrupted waiting for NATS MSG on " + subject, e);
                }
            }
        } finally {
            lock.unlock();
        }
    }

    private void readLoop() {
        try {
            while (running && in != null) {
                String line = readLine(in);
                if (line == null) {
                    return;
                }
                if (line.startsWith("INFO ")) {
                    lock.lock();
                    try {
                        serverInfo = line.substring(5).trim();
                        signal.signalAll();
                    } finally {
                        lock.unlock();
                    }
                } else if (line.equals("PING")) {
                    writeRaw("PONG\r\n".getBytes(StandardCharsets.US_ASCII));
                } else if (line.equals("PONG") || line.equals("+OK") || line.isBlank()) {
                    // ignore
                } else if (line.startsWith("-ERR")) {
                    if (driverObject != null) {
                        driverObject.log(DriverLogLevel.WARNING, "NATS " + line);
                    }
                } else if (line.startsWith("MSG ")) {
                    handleMsg(line);
                }
            }
        } catch (IOException e) {
            if (running && driverObject != null) {
                driverObject.log(DriverLogLevel.WARNING, "NATS reader stopped: " + e.getMessage());
            }
        } catch (DriverException e) {
            if (running && driverObject != null) {
                driverObject.log(DriverLogLevel.WARNING, "NATS reader error: " + e.getMessage());
            }
        } finally {
            connected = false;
        }
    }

    private void handleMsg(String header) throws IOException {
        // MSG <subject> <sid> [reply] <#bytes>
        String[] parts = header.split(" ");
        if (parts.length < 4) {
            return;
        }
        String subject = parts[1];
        String sid = parts[2];
        int sizeIndex = parts.length - 1;
        int size = Integer.parseInt(parts[sizeIndex]);
        byte[] payload = in.readNBytes(size);
        if (payload.length < size) {
            throw new IOException("Truncated NATS MSG payload");
        }
        // trailing CRLF
        in.read();
        in.read();
        String text = new String(payload, StandardCharsets.UTF_8);
        subjectBySid.putIfAbsent(sid, subject);
        lock.lock();
        try {
            lastPayloads.put(subject, text);
            signal.signalAll();
        } finally {
            lock.unlock();
        }
        for (Map.Entry<String, String> entry : pointSubjects.entrySet()) {
            if (subject.equals(entry.getValue())) {
                driverObject.updateVariable(entry.getKey(), DataRecord.single(VALUE_SCHEMA, Map.of(
                        "value", text,
                        "subject", subject,
                        "sid", sid
                )));
            }
        }
    }

    static String readLine(InputStream in) throws IOException {
        ByteArrayOutputStream line = new ByteArrayOutputStream();
        while (true) {
            int ch = in.read();
            if (ch < 0) {
                if (line.size() == 0) {
                    return null;
                }
                break;
            }
            if (ch == '\n') {
                break;
            }
            if (ch != '\r') {
                line.write(ch);
            }
        }
        return line.toString(StandardCharsets.UTF_8);
    }

    private static String escapeJson(String value) {
        return value.replace("\\", "\\\\").replace("\"", "\\\"");
    }

    private static String extractValue(DataRecord value) {
        if (value == null || value.rowCount() == 0) {
            return "";
        }
        Map<String, Object> row = value.firstRow();
        for (String key : List.of("value", "payload", "data", "text", "raw")) {
            Object candidate = row.get(key);
            if (candidate != null) {
                return String.valueOf(candidate);
            }
        }
        if (row.size() == 1) {
            return String.valueOf(row.values().iterator().next());
        }
        return row.toString();
    }
}
