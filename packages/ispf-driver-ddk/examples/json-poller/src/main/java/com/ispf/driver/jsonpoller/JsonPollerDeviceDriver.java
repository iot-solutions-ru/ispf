package com.ispf.driver.jsonpoller;

import com.ispf.core.model.DataRecord;
import com.ispf.core.model.DataSchema;
import com.ispf.core.model.FieldType;
import com.ispf.driver.DeviceDriver;
import com.ispf.driver.DriverException;
import com.ispf.driver.DriverMaturity;
import com.ispf.driver.DriverMetadata;

import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Reference DDK driver — JSON path poller stub (BL-144).
 * <p>
 * Point mapping format: {@code jsonPath:$.field} — returns a placeholder until HTTP fetch is wired.
 */
public class JsonPollerDeviceDriver implements DeviceDriver {

    private static final DataSchema VALUE_SCHEMA = DataSchema.builder("jsonPollerValue")
            .field("jsonPath", FieldType.STRING)
            .field("raw", FieldType.STRING)
            .field("connected", FieldType.BOOLEAN)
            .build();

    private static final DriverMetadata METADATA = new DriverMetadata(
            "json-poller",
            "JSON Poller Driver (DDK reference)",
            "0.1.0",
            "Polls JSON endpoints and maps jsonPath expressions to variables (stub)",
            "ISPF",
            Map.of(
                    "baseUrl", "http://127.0.0.1:8080",
                    "timeoutMs", "5000",
                    "pollIntervalMs", "5000"
            ),
            DriverMaturity.BETA,
            Set.of("read")
    );

    private DriverObject driverObject;
    private String baseUrl = "http://127.0.0.1:8080";
    private final Map<String, JsonPollerPoint> points = new ConcurrentHashMap<>();
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
        if ("baseUrl".equals(key)) {
            baseUrl = value.trim();
        }
    }

    @Override
    public void connect() throws DriverException {
        connected = true;
        driverObject.log(DriverLogLevel.INFO, "JSON poller stub connected to " + baseUrl);
    }

    @Override
    public void disconnect() {
        connected = false;
        points.clear();
    }

    @Override
    public boolean isConnected() {
        return connected;
    }

    @Override
    public void readPoints(Map<String, String> pointMappings) throws DriverException {
        requireConnected();
        for (Map.Entry<String, String> entry : pointMappings.entrySet()) {
            JsonPollerPoint point = JsonPollerPoint.parse(entry.getValue());
            points.put(entry.getKey(), point);
            driverObject.updateVariable(entry.getKey(), DataRecord.single(VALUE_SCHEMA, Map.of(
                    "jsonPath", point.jsonPath(),
                    "raw", "{\"stub\":true,\"path\":\"" + point.jsonPath() + "\"}",
                    "connected", true
            )));
        }
    }

    @Override
    public void writePoint(String pointId, DataRecord value) throws DriverException {
        throw new DriverException("JSON poller driver is read-only");
    }

    private void requireConnected() throws DriverException {
        if (!connected) {
            throw new DriverException("Not connected");
        }
    }
}
