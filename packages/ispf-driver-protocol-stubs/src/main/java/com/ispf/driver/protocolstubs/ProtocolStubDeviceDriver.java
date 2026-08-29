package com.ispf.driver.protocolstubs;

import com.ispf.core.model.DataRecord;
import com.ispf.core.model.DataSchema;
import com.ispf.core.model.FieldType;
import com.ispf.driver.DeviceDriver;
import com.ispf.driver.DriverException;
import com.ispf.driver.DriverMaturity;
import com.ispf.driver.DriverMetadata;

import java.net.InetSocketAddress;
import java.net.Socket;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Shared TCP reachability shell for protocol catalog stubs (maturity {@code STUB}).
 */
public abstract class ProtocolStubDeviceDriver implements DeviceDriver {

    protected static final DataSchema STUB_SCHEMA = DataSchema.builder("protocolStubResult")
            .field("connected", FieldType.BOOLEAN)
            .field("value", FieldType.STRING)
            .field("limitation", FieldType.STRING)
            .build();

    private final DriverMetadata metadata;
    private final String limitation;
    private final int defaultPort;

    private DriverObject driverObject;
    private String host = "127.0.0.1";
    private int port;
    private int timeoutMs = 5000;
    private final Map<String, String> points = new ConcurrentHashMap<>();
    private volatile boolean connected;

    protected ProtocolStubDeviceDriver(
            String driverId,
            String displayName,
            String description,
            int defaultPort
    ) {
        this.defaultPort = defaultPort;
        this.port = defaultPort;
        this.limitation = description + " — connectivity stub only (no protocol codec)";
        this.metadata = new DriverMetadata(
                driverId,
                displayName,
                "0.1.0",
                this.limitation,
                "ISPF",
                Map.of(
                        "host", "127.0.0.1",
                        "port", String.valueOf(defaultPort),
                        "timeoutMs", "5000",
                        "pollIntervalMs", "30000"
                ),
                DriverMaturity.STUB,
                Set.of("read")
        );
    }

    @Override
    public final DriverMetadata metadata() {
        return metadata;
    }

    @Override
    public final void initialize(DriverObject driverObject) {
        this.driverObject = driverObject;
        this.host = "127.0.0.1";
        this.port = defaultPort;
        this.timeoutMs = 5000;
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
    public final void connect() throws DriverException {
        connected = true;
        driverObject.log(DriverLogLevel.WARNING, limitation);
        driverObject.log(
                DriverLogLevel.INFO,
                metadata.id() + " stub ready for " + host + ":" + port
        );
    }

    @Override
    public final void disconnect() {
        connected = false;
        points.clear();
    }

    @Override
    public final boolean isConnected() {
        return connected;
    }

    @Override
    public final void readPoints(Map<String, String> pointMappings) throws DriverException {
        if (!isConnected()) {
            throw new DriverException("Not connected");
        }
        points.clear();
        boolean reachable = tcpConnect(host, port);
        String value = reachable ? "endpoint-open" : "endpoint-closed";
        for (Map.Entry<String, String> entry : pointMappings.entrySet()) {
            points.put(entry.getKey(), entry.getValue() == null ? "connected" : entry.getValue());
            driverObject.updateVariable(entry.getKey(), DataRecord.single(STUB_SCHEMA, Map.of(
                    "connected", reachable,
                    "value", value,
                    "limitation", limitation
            )));
        }
    }

    @Override
    public final void writePoint(String pointId, DataRecord value) throws DriverException {
        throw new DriverException(metadata.id() + " driver is read-only stub in v0.1");
    }

    private boolean tcpConnect(String targetHost, int targetPort) {
        try (Socket socket = new Socket()) {
            socket.connect(new InetSocketAddress(targetHost, targetPort), timeoutMs);
            return true;
        } catch (Exception e) {
            return false;
        }
    }
}
