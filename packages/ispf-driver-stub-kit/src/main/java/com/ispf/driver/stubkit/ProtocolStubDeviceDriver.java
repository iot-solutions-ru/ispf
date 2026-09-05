package com.ispf.driver.stubkit;

import com.ispf.core.model.DataRecord;
import com.ispf.core.model.DataSchema;
import com.ispf.core.model.FieldType;
import com.ispf.driver.DeviceDriver;
import com.ispf.driver.DriverException;
import com.ispf.driver.DriverMaturity;
import com.ispf.driver.DriverMetadata;

import java.net.InetSocketAddress;
import java.net.Socket;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Shared lab shell for protocol catalog stubs (maturity {@code STUB}).
 * <p>
 * Provides TCP reachability probe on read and an in-memory write loopback for
 * console / CI contract tests. Does <strong>not</strong> implement a protocol codec —
 * do not claim {@code PRODUCTION} until a real stack lands.
 * <p>
 * Clean-room ISPF code, Apache-2.0 — no third-party protocol stacks.
 */
public abstract class ProtocolStubDeviceDriver implements DeviceDriver {

    protected static final DataSchema STUB_SCHEMA = DataSchema.builder("protocolStubResult")
            .field("connected", FieldType.BOOLEAN)
            .field("value", FieldType.STRING)
            .field("mode", FieldType.STRING)
            .field("limitation", FieldType.STRING)
            .build();

    private final DriverMetadata metadata;
    private final String limitation;
    private final int defaultPort;

    private DriverObject driverObject;
    private String host = "127.0.0.1";
    private int port;
    private int timeoutMs = 5000;
    private final Map<String, String> writtenValues = new ConcurrentHashMap<>();
    private volatile boolean connected;

    protected ProtocolStubDeviceDriver(
            String driverId,
            String displayName,
            String description,
            int defaultPort
    ) {
        this.defaultPort = defaultPort;
        this.port = defaultPort;
        this.limitation = description
                + " — lab stub (TCP probe + memory loopback write; no protocol codec)";
        this.metadata = new DriverMetadata(
                driverId,
                displayName,
                "0.2.0",
                this.limitation,
                "ISPF",
                Map.of(
                        "host", "127.0.0.1",
                        "port", String.valueOf(defaultPort),
                        "timeoutMs", "5000",
                        "pollIntervalMs", "30000"
                ),
                DriverMaturity.STUB,
                Set.of("read", "write")
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
        writtenValues.clear();
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
        writtenValues.clear();
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
        boolean reachable = tcpConnect(host, port);
        String probeValue = reachable ? "endpoint-open" : "endpoint-closed";
        for (Map.Entry<String, String> entry : pointMappings.entrySet()) {
            String pointId = entry.getKey();
            String written = writtenValues.get(pointId);
            boolean loopback = written != null;
            driverObject.updateVariable(pointId, DataRecord.single(STUB_SCHEMA, Map.of(
                    "connected", reachable || loopback,
                    "value", loopback ? written : probeValue,
                    "mode", loopback ? "loopback" : "probe",
                    "limitation", limitation
            )));
        }
    }

    @Override
    public final void writePoint(String pointId, DataRecord value) throws DriverException {
        if (!isConnected()) {
            throw new DriverException("Not connected");
        }
        if (pointId == null || pointId.isBlank()) {
            throw new DriverException("pointId is required");
        }
        String stored = extractWritableValue(value);
        writtenValues.put(pointId, stored);
        driverObject.updateVariable(pointId, DataRecord.single(STUB_SCHEMA, Map.of(
                "connected", true,
                "value", stored,
                "mode", "loopback",
                "limitation", limitation
        )));
        driverObject.log(
                DriverLogLevel.INFO,
                metadata.id() + " lab loopback write " + pointId + "=" + stored
        );
    }

    private static String extractWritableValue(DataRecord value) {
        if (value == null || value.rowCount() == 0) {
            return "";
        }
        Map<String, Object> row = value.firstRow();
        for (String key : List.of("value", "payload", "data", "text")) {
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

    private boolean tcpConnect(String targetHost, int targetPort) {
        try (Socket socket = new Socket()) {
            socket.connect(new InetSocketAddress(targetHost, targetPort), timeoutMs);
            return true;
        } catch (Exception e) {
            return false;
        }
    }
}
