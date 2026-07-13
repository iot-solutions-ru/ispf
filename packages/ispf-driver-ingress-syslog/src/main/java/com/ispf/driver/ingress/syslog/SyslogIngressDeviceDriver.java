package com.ispf.driver.ingress.syslog;

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
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;

/**
 * UDP Syslog listener — publishes each datagram via {@link DriverObject#updateVariable}
 * (configure {@code telemetryPublishMode=EVENT_JOURNAL_ONLY} on device binding).
 */
public class SyslogIngressDeviceDriver implements DeviceDriver {

    private static final DataSchema MESSAGE_SCHEMA = DataSchema.builder("syslogMessage")
            .field("message", FieldType.STRING)
            .field("sourceHost", FieldType.STRING)
            .field("sourcePort", FieldType.INTEGER)
            .field("bytes", FieldType.INTEGER)
            .build();

    private static final DataSchema STATS_SCHEMA = DataSchema.builder("syslogStats")
            .field("messagesReceived", FieldType.LONG)
            .field("lastMessage", FieldType.STRING)
            .field("listening", FieldType.BOOLEAN)
            .build();

    private static final DriverMetadata METADATA = new DriverMetadata(
            "ingress-syslog",
            "Syslog UDP Ingress",
            "0.1.0",
            "Listens for RFC5424/3164 syslog datagrams and forwards to ISPF ingress",
            "ISPF",
            Map.of(
                    "port", "514",
                    "bindAddress", "0.0.0.0",
                    "bufferSize", "8192",
                    "telemetryPublishMode", "EVENT_JOURNAL_ONLY",
                    "ingressEventName", "itmSyslogAlert"
            ),
            DriverMaturity.BETA,
            java.util.Set.of("read")
    );

    private DriverObject driverObject;
    private int port = 514;
    private String bindAddress = "0.0.0.0";
    private int bufferSize = 8192;
    private DatagramSocket socket;
    private Thread listenerThread;
    private volatile boolean connected;
    private final AtomicLong messageCounter = new AtomicLong();
    private volatile String lastMessage = "";
    private DriverIngressBuffer<Long, IngressSample> ingressBuffer;

    @Override
    public DriverMetadata metadata() {
        return METADATA;
    }

    @Override
    public void initialize(DriverObject driverObject) {
        this.driverObject = driverObject;
        Map<String, String> config = driverObject.configuration();
        config.forEach(this::applyConfig);
        boolean fifo = DriverIngress.resolveFifoIngress(config, false);
        ingressBuffer = new DriverIngressBuffer<>(
                DriverIngress.resolveThreads(config, 2),
                DriverIngress.resolveCapacity(config, 4096),
                this::handleIngressSample,
                "ingress-syslog",
                fifo
        );
    }

    private void applyConfig(String key, String value) {
        if (value == null || value.isBlank()) {
            return;
        }
        switch (key) {
            case "port" -> port = Integer.parseInt(value.trim());
            case "bindAddress" -> bindAddress = value.trim();
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
            listenerThread = new Thread(this::listenLoop, "ingress-syslog-" + port);
            listenerThread.setDaemon(true);
            listenerThread.start();
            driverObject.log(DriverLogLevel.INFO, "Syslog ingress listening on UDP " + port);
        } catch (SocketException e) {
            connected = false;
            throw new DriverException("Syslog bind failed on port " + port, e);
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
        requireConnected();
        ingressBuffer.flushNow();
        DataRecord stats = DataRecord.single(STATS_SCHEMA, Map.of(
                "messagesReceived", messageCounter.get(),
                "lastMessage", lastMessage,
                "listening", connected
        ));
        for (String pointId : pointMappings.keySet()) {
            driverObject.updateVariable(pointId, stats);
        }
    }

    @Override
    public void writePoint(String pointId, DataRecord value) throws DriverException {
        throw new DriverException("Syslog ingress is read-only");
    }

    private void listenLoop() {
        byte[] buffer = new byte[bufferSize];
        while (connected && socket != null && !socket.isClosed()) {
            try {
                DatagramPacket packet = new DatagramPacket(buffer, buffer.length);
                socket.receive(packet);
                String text = new String(packet.getData(), packet.getOffset(), packet.getLength(), StandardCharsets.UTF_8);
                String host = packet.getAddress() != null ? packet.getAddress().getHostAddress() : "";
                int sourcePort = packet.getPort();
                lastMessage = text;
                long seq = messageCounter.incrementAndGet();
                ingressBuffer.submit(seq, new IngressSample(text, host, sourcePort, packet.getLength(), Instant.now()));
            } catch (IOException e) {
                if (connected) {
                    driverObject.log(DriverLogLevel.WARNING, "Syslog receive error: " + e.getMessage());
                }
            }
        }
    }

    private void handleIngressSample(Long ignored, IngressSample sample) {
        DataRecord record = DataRecord.single(MESSAGE_SCHEMA, Map.of(
                "message", sample.message(),
                "sourceHost", sample.sourceHost(),
                "sourcePort", sample.sourcePort(),
                "bytes", sample.bytes()
        ));
        driverObject.updateVariable("lastDatagram", record, sample.observedAt());
    }

    private void requireConnected() throws DriverException {
        if (!connected) {
            throw new DriverException("Not connected");
        }
    }

    private record IngressSample(String message, String sourceHost, int sourcePort, int bytes, Instant observedAt) {
    }
}
