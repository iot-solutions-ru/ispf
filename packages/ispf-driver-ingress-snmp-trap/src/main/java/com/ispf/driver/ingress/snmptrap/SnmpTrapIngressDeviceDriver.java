package com.ispf.driver.ingress.snmptrap;

import com.ispf.core.model.DataRecord;
import com.ispf.core.model.DataSchema;
import com.ispf.core.model.FieldType;
import com.ispf.driver.DeviceDriver;
import com.ispf.driver.DriverException;
import com.ispf.driver.DriverMaturity;
import com.ispf.driver.DriverMetadata;
import com.ispf.driver.ingress.DriverIngress;
import com.ispf.driver.ingress.DriverIngressBuffer;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.SocketException;
import java.time.Instant;
import java.util.Base64;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;

/** SNMP trap UDP ingress (v1/v2c BER payload as opaque bytes + hex preview). */
public class SnmpTrapIngressDeviceDriver implements DeviceDriver {

    private static final DataSchema TRAP_SCHEMA = DataSchema.builder("snmpTrap")
            .field("payloadBase64", FieldType.STRING)
            .field("payloadHex", FieldType.STRING)
            .field("sourceHost", FieldType.STRING)
            .field("bytes", FieldType.INTEGER)
            .build();

    private static final DataSchema STATS_SCHEMA = DataSchema.builder("snmpTrapStats")
            .field("trapsReceived", FieldType.LONG)
            .field("listening", FieldType.BOOLEAN)
            .build();

    private static final DriverMetadata METADATA = new DriverMetadata(
            "ingress-snmp-trap",
            "SNMP Trap UDP Ingress",
            "0.1.0",
            "Listens for SNMP traps on UDP/162 and forwards to ISPF ingress",
            "ISPF",
            Map.of(
                    "port", "162",
                    "bindAddress", "0.0.0.0",
                    "bufferSize", "65535",
                    "telemetryPublishMode", "EVENT_JOURNAL_ONLY",
                    "ingressEventName", "itmSnmpTrap"
            ),
            DriverMaturity.BETA,
            java.util.Set.of("read")
    );

    private DriverObject driverObject;
    private int port = 162;
    private int bufferSize = 65535;
    private DatagramSocket socket;
    private Thread listenerThread;
    private volatile boolean connected;
    private final AtomicLong trapCounter = new AtomicLong();
    private DriverIngressBuffer<Long, TrapSample> ingressBuffer;

    @Override
    public DriverMetadata metadata() {
        return METADATA;
    }

    @Override
    public void initialize(DriverObject driverObject) {
        this.driverObject = driverObject;
        Map<String, String> config = driverObject.configuration();
        config.forEach(this::applyConfig);
        ingressBuffer = new DriverIngressBuffer<>(
                DriverIngress.resolveThreads(config, 2),
                DriverIngress.resolveCapacity(config, 4096),
                (ignored, sample) -> publishTrap(sample),
                "ingress-snmp-trap",
                DriverIngress.resolveFifoIngress(config, false)
        );
    }

    private void applyConfig(String key, String value) {
        if (value == null || value.isBlank()) {
            return;
        }
        switch (key) {
            case "port" -> port = Integer.parseInt(value.trim());
            case "bufferSize" -> bufferSize = Integer.parseInt(value.trim());
            default -> { }
        }
    }

    @Override
    public void connect() throws DriverException {
        disconnect();
        try {
            socket = new DatagramSocket(port);
            socket.setReuseAddress(true);
            connected = true;
            listenerThread = new Thread(this::listenLoop, "ingress-snmp-trap-" + port);
            listenerThread.setDaemon(true);
            listenerThread.start();
            driverObject.log(DriverLogLevel.INFO, "SNMP trap ingress listening on UDP " + port);
        } catch (SocketException e) {
            connected = false;
            throw new DriverException("SNMP trap bind failed on port " + port, e);
        }
    }

    @Override
    public void disconnect() {
        connected = false;
        if (socket != null) {
            socket.close();
            socket = null;
        }
        if (listenerThread != null) {
            listenerThread.interrupt();
            listenerThread = null;
        }
        if (ingressBuffer != null) {
            ingressBuffer.shutdown();
        }
    }

    @Override
    public boolean isConnected() {
        return connected;
    }

    @Override
    public void readPoints(Map<String, String> pointMappings) throws DriverException {
        if (!connected) {
            throw new DriverException("Not connected");
        }
        ingressBuffer.flushNow();
        DataRecord stats = DataRecord.single(STATS_SCHEMA, Map.of(
                "trapsReceived", trapCounter.get(),
                "listening", connected
        ));
        for (String pointId : pointMappings.keySet()) {
            driverObject.updateVariable(pointId, stats);
        }
    }

    @Override
    public void writePoint(String pointId, DataRecord value) throws DriverException {
        throw new DriverException("SNMP trap ingress is read-only");
    }

    private void listenLoop() {
        byte[] buffer = new byte[bufferSize];
        while (connected && socket != null && !socket.isClosed()) {
            try {
                DatagramPacket packet = new DatagramPacket(buffer, buffer.length);
                socket.receive(packet);
                byte[] payload = new byte[packet.getLength()];
                System.arraycopy(packet.getData(), packet.getOffset(), payload, 0, packet.getLength());
                String host = packet.getAddress() != null ? packet.getAddress().getHostAddress() : "";
                long seq = trapCounter.incrementAndGet();
                ingressBuffer.submit(seq, new TrapSample(payload, host, Instant.now()));
            } catch (IOException e) {
                if (connected) {
                    driverObject.log(DriverLogLevel.WARNING, "SNMP trap receive error: " + e.getMessage());
                }
            }
        }
    }

    private void publishTrap(TrapSample sample) {
        String base64 = Base64.getEncoder().encodeToString(sample.payload());
        String hex = toHexPreview(sample.payload(), 64);
        DataRecord record = DataRecord.single(TRAP_SCHEMA, Map.of(
                "payloadBase64", base64,
                "payloadHex", hex,
                "sourceHost", sample.sourceHost(),
                "bytes", sample.payload().length
        ));
        driverObject.updateVariable("lastTrap", record, sample.observedAt());
    }

    private static String toHexPreview(byte[] payload, int maxBytes) {
        int limit = Math.min(payload.length, maxBytes);
        StringBuilder sb = new StringBuilder(limit * 2);
        for (int i = 0; i < limit; i++) {
            sb.append(String.format("%02x", payload[i]));
        }
        if (payload.length > maxBytes) {
            sb.append("...");
        }
        return sb.toString();
    }

    private record TrapSample(byte[] payload, String sourceHost, Instant observedAt) {
    }
}
