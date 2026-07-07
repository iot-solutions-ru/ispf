package com.ispf.driver.template;

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
 * Minimal custom driver stub — copy this module layout from {@code packages/ispf-driver-ddk/template/}.
 */
public class TemplateDeviceDriver implements DeviceDriver {

    private static final DataSchema VALUE_SCHEMA = DataSchema.builder("templateValue")
            .field("value", FieldType.STRING)
            .field("connected", FieldType.BOOLEAN)
            .build();

    private static final DriverMetadata METADATA = new DriverMetadata(
            "acme-widget",
            "Acme Widget Driver (template)",
            "0.1.0",
            "Replace with your protocol description",
            "Your Company",
            Map.of(
                    "host", "127.0.0.1",
                    "port", "9500",
                    "timeoutMs", "5000",
                    "pollIntervalMs", "1000"
            ),
            DriverMaturity.BETA,
            Set.of("read", "write")
    );

    private DriverObject driverObject;
    private final Map<String, TemplatePoint> points = new ConcurrentHashMap<>();
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
            case "host", "port", "timeoutMs", "pollIntervalMs" -> { }
            default -> driverObject.log(DriverLogLevel.DEBUG, "Unknown config key: " + key);
        }
    }

    @Override
    public void connect() throws DriverException {
        connected = true;
        driverObject.log(DriverLogLevel.INFO, "Template driver connected (replace with real session open)");
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
            TemplatePoint point = TemplatePoint.parse(entry.getValue());
            points.put(entry.getKey(), point);
            driverObject.updateVariable(entry.getKey(), DataRecord.single(VALUE_SCHEMA, Map.of(
                    "value", "TEMPLATE_OK:" + point.address(),
                    "connected", true
            )));
        }
    }

    @Override
    public void writePoint(String pointId, DataRecord value) throws DriverException {
        requireConnected();
        TemplatePoint point = points.get(pointId);
        if (point == null) {
            throw new DriverException("Unknown point: " + pointId);
        }
        Object raw = value.firstRow().get("value");
        if (raw == null) {
            raw = value.firstRow().get("raw");
        }
        driverObject.updateVariable(pointId, DataRecord.single(VALUE_SCHEMA, Map.of(
                "value", String.valueOf(raw),
                "connected", true
        )));
    }

    private void requireConnected() throws DriverException {
        if (!connected) {
            throw new DriverException("Not connected");
        }
    }
}
