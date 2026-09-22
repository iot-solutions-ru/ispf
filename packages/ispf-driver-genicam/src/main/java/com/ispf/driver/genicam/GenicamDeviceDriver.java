package com.ispf.driver.genicam;

import com.ispf.core.model.DataRecord;
import com.ispf.core.model.DataSchema;
import com.ispf.core.model.FieldType;
import com.ispf.driver.DeviceDriver;
import com.ispf.driver.DriverException;
import com.ispf.driver.DriverMetadata;
import com.ispf.driver.genicam.codec.GenicamCodec;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * GenICam / GigE Vision driver — GVCP discovery over UDP (control-protocol header).
 * <p>
 * Sends a GVCP discovery command starting {@code 42 01 00 02}. Point mapping is a label
 * (for example {@code camera} or {@code discovery}). Not GVSP streaming, not GenTL / camera SDK.
 * <p>
 * Clean-room ISPF code, Apache-2.0 — JDK {@link DatagramSocket} only.
 */
public class GenicamDeviceDriver implements DeviceDriver {

    private static final DataSchema VALUE_SCHEMA = DataSchema.builder("genicamValue")
            .field("value", FieldType.STRING)
            .field("feature", FieldType.STRING)
            .field("raw", FieldType.STRING)
            .build();

    private static final DriverMetadata METADATA = new DriverMetadata(
            "genicam",
            "GenICam Driver",
            "0.1.0",
            "GigE Vision GVCP discovery over UDP (control-protocol header starting 42 01 00 02);"
                    + " not GVSP stream / GenTL / camera SDK",
            "ISPF",
            Map.of(
                    "host", "127.0.0.1",
                    "port", "3956",
                    "timeoutMs", "3000"
            ),
            null,
            Set.of("read")
    );

    private DriverObject driverObject;
    private String host = "127.0.0.1";
    private int port = 3956;
    private int timeoutMs = 3000;
    private DatagramSocket socket;
    private final AtomicInteger requestId = new AtomicInteger(1);
    private final Map<String, String> labels = new ConcurrentHashMap<>();
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
        disconnect();
        try {
            DatagramSocket next = new DatagramSocket();
            next.setSoTimeout(timeoutMs);
            socket = next;
            connected = true;
            driverObject.log(DriverLogLevel.INFO,
                    "GenICam GVCP UDP ready for " + host + ":" + port);
        } catch (IOException e) {
            closeSocket();
            throw new DriverException("GenICam connect failed for " + host + ":" + port, e);
        }
    }

    @Override
    public void disconnect() {
        connected = false;
        labels.clear();
        closeSocket();
    }

    @Override
    public boolean isConnected() {
        return connected && socket != null && !socket.isClosed();
    }

    @Override
    public void readPoints(Map<String, String> pointMappings) throws DriverException {
        ensureConnected();
        for (Map.Entry<String, String> entry : pointMappings.entrySet()) {
            String pointId = entry.getKey();
            String label = entry.getValue() == null || entry.getValue().isBlank()
                    ? pointId : entry.getValue().trim();
            labels.put(pointId, label);
            try {
                byte[] reply = discover();
                driverObject.updateVariable(pointId, DataRecord.single(VALUE_SCHEMA, Map.of(
                        "value", reply.length > 0 ? "ok" : "",
                        "feature", label,
                        "raw", toHex(reply)
                )));
            } catch (IOException e) {
                throw new DriverException("GenICam GVCP discovery failed for " + host + ":" + port, e);
            }
        }
    }

    @Override
    public void writePoint(String pointId, DataRecord value) throws DriverException {
        throw new DriverException("genicam GVCP discovery is read-only");
    }

    static byte[] buildDiscoveryCommand(int requestId) {
        return GenicamCodec.encodeDiscoveryCommand(requestId);
    }

    private byte[] discover() throws IOException {
        byte[] command = GenicamCodec.encodeDiscoveryCommand(requestId.getAndIncrement());
        InetAddress address = InetAddress.getByName(host);
        DatagramPacket outbound = new DatagramPacket(command, command.length, new InetSocketAddress(address, port));
        socket.send(outbound);
        byte[] buffer = new byte[1500];
        DatagramPacket inbound = new DatagramPacket(buffer, buffer.length);
        socket.receive(inbound);
        byte[] reply = new byte[inbound.getLength()];
        System.arraycopy(inbound.getData(), inbound.getOffset(), reply, 0, inbound.getLength());
        return reply;
    }

    static String toHex(byte[] bytes) {
        if (bytes == null || bytes.length == 0) {
            return "";
        }
        StringBuilder sb = new StringBuilder(bytes.length * 2);
        for (byte b : bytes) {
            sb.append(String.format(Locale.ROOT, "%02X", b & 0xFF));
        }
        return sb.toString();
    }

    private void ensureConnected() throws DriverException {
        if (!isConnected()) {
            throw new DriverException("Not connected");
        }
    }

    private void closeSocket() {
        DatagramSocket current = socket;
        socket = null;
        if (current != null) {
            current.close();
        }
    }
}
