package com.ispf.driver.icmp;

import com.ispf.core.model.DataRecord;
import com.ispf.core.model.DataSchema;
import com.ispf.core.model.FieldType;
import com.ispf.driver.DeviceDriver;
import com.ispf.driver.DriverException;
import com.ispf.driver.DriverMetadata;

import java.net.InetAddress;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * ICMP reachability driver — uses {@link InetAddress#isReachable(int)} (platform ping).
 * Point mapping: hostname or IP per variable; blank uses configured default host.
 */
public class IcmpDeviceDriver implements DeviceDriver {

    private static final DataSchema PING_SCHEMA = DataSchema.builder("icmpPing")
            .field("reachable", FieldType.BOOLEAN)
            .field("latencyMs", FieldType.DOUBLE)
            .field("host", FieldType.STRING)
            .build();

    private static final DriverMetadata METADATA = new DriverMetadata(
            "icmp",
            "ICMP / Ping Driver",
            "0.1.0",
            "Host reachability and round-trip latency via ICMP (platform isReachable)",
            "ISPF",
            Map.of(
                    "host", "127.0.0.1",
                    "timeoutMs", "3000",
                    "pollIntervalMs", "30000"
            )
    );

    private DriverObject driverObject;
    private String defaultHost = "127.0.0.1";
    private int timeoutMs = 3000;
    private final Map<String, String> points = new ConcurrentHashMap<>();
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
            case "host" -> defaultHost = value.trim();
            case "timeoutMs" -> timeoutMs = Integer.parseInt(value.trim());
            default -> { }
        }
    }

    @Override
    public void connect() throws DriverException {
        connected = true;
        driverObject.log(DriverLogLevel.INFO, "ICMP driver ready (defaultHost=" + defaultHost + ")");
    }

    @Override
    public void disconnect() {
        connected = false;
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
        for (Map.Entry<String, String> entry : pointMappings.entrySet()) {
            String host = entry.getValue() == null || entry.getValue().isBlank()
                    ? defaultHost
                    : entry.getValue().trim();
            points.put(entry.getKey(), host);
            driverObject.updateVariable(entry.getKey(), ping(host));
        }
    }

    @Override
    public void writePoint(String pointId, DataRecord value) throws DriverException {
        throw new DriverException("ICMP driver is read-only");
    }

    private DataRecord ping(String host) throws DriverException {
        try {
            long start = System.nanoTime();
            InetAddress address = InetAddress.getByName(host);
            boolean reachable = address.isReachable(timeoutMs);
            double latencyMs = reachable ? (System.nanoTime() - start) / 1_000_000.0 : -1.0;
            return DataRecord.single(PING_SCHEMA, Map.of(
                    "reachable", reachable,
                    "latencyMs", latencyMs,
                    "host", host
            ));
        } catch (Exception e) {
            throw new DriverException("ICMP ping failed for " + host, e);
        }
    }
}
