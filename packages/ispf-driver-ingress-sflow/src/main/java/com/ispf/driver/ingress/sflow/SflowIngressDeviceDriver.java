package com.ispf.driver.ingress.sflow;

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
import java.net.InetAddress;
import java.time.Instant;
import java.util.Base64;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;

/** sFlow v5 UDP ingress (raw datagram capture; flow decode in correlator/rules). */
public class SflowIngressDeviceDriver implements DeviceDriver {

    private static final DataSchema SAMPLE_SCHEMA = DataSchema.builder("sflowDatagram")
            .field("payloadBase64", FieldType.STRING)
            .field("sourceHost", FieldType.STRING)
            .field("bytes", FieldType.INTEGER)
            .build();

    private static final DataSchema STATS_SCHEMA = DataSchema.builder("sflowStats")
            .field("datagramsReceived", FieldType.LONG)
            .field("listening", FieldType.BOOLEAN)
            .build();

    private static final DriverMetadata METADATA = new DriverMetadata(
            "ingress-sflow",
            "sFlow UDP Ingress",
            "0.1.0",
            "Listens for sFlow v5 samples on UDP/6343",
            "ISPF",
            Map.of(
                    "port", "6343",
                    "bindAddress", "0.0.0.0",
                    "bufferSize", "65535",
                    "telemetryPublishMode", "EVENT_JOURNAL_ONLY",
                    "ingressEventName", "itmSflowSample"
            ),
            DriverMaturity.BETA,
            java.util.Set.of("read")
    );

    private DriverObject driverObject;
    private int port = 6343;
    private String bindAddress = "0.0.0.0";
    private int bufferSize = 65535;
    private DatagramSocket socket;
    private Thread listenerThread;
    private volatile boolean connected;
    private final AtomicLong sampleCounter = new AtomicLong();
    private DriverIngressBuffer<Long, Sample> ingressBuffer;

    @Override
    public DriverMetadata metadata() {
        return METADATA;
    }

    @Override
    public void initialize(DriverObject driverObject) {
        this.driverObject = driverObject;
        Map<String, String> config = driverObject.configuration();
        config.forEach(this::applyConfig);
        startIngress(config);
    }

    private void startIngress(Map<String, String> config) {
        ingressBuffer = new DriverIngressBuffer<>(
                DriverIngress.resolveThreads(config, 2),
                DriverIngress.resolveCapacity(config, 8192),
                (ignored, sample) -> publishSample(sample),
                "ingress-sflow",
                DriverIngress.resolveFifoIngress(config, false)
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
        startIngress(driverObject.configuration());
        try {
            socket = bindAddress.isBlank()
                    ? new DatagramSocket(port)
                    : new DatagramSocket(port, InetAddress.getByName(bindAddress.trim()));
            socket.setReuseAddress(true);
            connected = true;
            listenerThread = new Thread(this::listenLoop, "ingress-sflow-" + port);
            listenerThread.setDaemon(true);
            listenerThread.start();
            driverObject.log(DriverLogLevel.INFO, "sFlow ingress listening on UDP " + port);
        } catch (IOException e) {
            connected = false;
            throw new DriverException("sFlow bind failed on port " + port, e);
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
                "datagramsReceived", sampleCounter.get(),
                "listening", connected
        ));
        for (String pointId : pointMappings.keySet()) {
            driverObject.updateVariable(pointId, stats);
        }
    }

    @Override
    public void writePoint(String pointId, DataRecord value) throws DriverException {
        throw new DriverException("sFlow ingress is read-only");
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
                long seq = sampleCounter.incrementAndGet();
                ingressBuffer.submit(seq, new Sample(payload, host, Instant.now()));
            } catch (IOException e) {
                if (connected) {
                    driverObject.log(DriverLogLevel.WARNING, "sFlow receive error: " + e.getMessage());
                }
            }
        }
    }

    private void publishSample(Sample sample) {
        DataRecord record = DataRecord.single(SAMPLE_SCHEMA, Map.of(
                "payloadBase64", Base64.getEncoder().encodeToString(sample.payload()),
                "sourceHost", sample.sourceHost(),
                "bytes", sample.payload().length
        ));
        driverObject.updateVariable("lastDatagram", record, sample.observedAt());
    }

    private record Sample(byte[] payload, String sourceHost, Instant observedAt) {
    }
}
