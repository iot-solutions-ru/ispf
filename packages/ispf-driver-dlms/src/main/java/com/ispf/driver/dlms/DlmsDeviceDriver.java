package com.ispf.driver.dlms;

import com.ispf.core.model.DataRecord;
import com.ispf.core.model.DataSchema;
import com.ispf.core.model.FieldType;
import com.ispf.driver.DeviceDriver;
import com.ispf.driver.DriverException;
import com.ispf.driver.DriverMetadata;
import com.ispf.driver.DriverMaturity;

import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * DLMS/COSEM meter driver — Gurux client with read/write over TCP WRAPPER.
 * <p>
 * Point mapping: {@code logicalDevice:obis[:objectType[:attribute]]}.
 */
public class DlmsDeviceDriver implements DeviceDriver {

    private static final DriverMetadata METADATA = new DriverMetadata(
            "dlms",
            "DLMS/COSEM Driver",
            "0.2.0",
            "DLMS/COSEM smart meters over TCP WRAPPER (Gurux read/write)",
            "ISPF",
            Map.of(
                    "host", "127.0.0.1",
                    "port", "4059",
                    "timeoutMs", "10000",
                    "clientAddress", "16",
                    "serverAddress", "1",
                    "logicalDevice", "1",
                    "pollIntervalMs", "60000"
            ),
            DriverMaturity.BETA,
            Set.of("read", "write")
    );

    private static final DataSchema VALUE_SCHEMA = DataSchema.builder("dlmsValue")
            .field("value", FieldType.STRING)
            .field("obis", FieldType.STRING)
            .field("logicalDevice", FieldType.INTEGER)
            .field("objectType", FieldType.STRING)
            .field("attributeIndex", FieldType.INTEGER)
            .field("quality", FieldType.STRING)
            .build();

    private DriverObject driverObject;
    private DlmsClientCommunicator communicator;
    private String host = "127.0.0.1";
    private int port = 4059;
    private int timeoutMs = 10000;
    private int clientAddress = 16;
    private int logicalDevice = 1;
    private final Map<String, DlmsPoint> points = new ConcurrentHashMap<>();

    @Override
    public DriverMetadata metadata() {
        return METADATA;
    }

    @Override
    public void initialize(DriverObject driverObject) {
        this.driverObject = driverObject;
        driverObject.configuration().forEach(this::applyConfig);
        readConfig("host", value -> host = value);
        readConfig("port", value -> port = Integer.parseInt(value));
        readConfig("timeoutMs", value -> timeoutMs = Integer.parseInt(value));
        readConfig("clientAddress", value -> clientAddress = Integer.parseInt(value));
        readConfig("logicalDevice", value -> logicalDevice = Integer.parseInt(value));
    }

    private void applyConfig(String key, String value) {
        if (value == null || value.isBlank()) {
            return;
        }
        switch (key) {
            case "host" -> host = value.trim();
            case "port" -> port = Integer.parseInt(value.trim());
            case "timeoutMs" -> timeoutMs = Integer.parseInt(value.trim());
            case "clientAddress" -> clientAddress = Integer.parseInt(value.trim());
            case "logicalDevice" -> logicalDevice = Integer.parseInt(value.trim());
            default -> { }
        }
    }

    @Override
    public void connect() throws DriverException {
        disconnect();
        communicator = new DlmsClientCommunicator(host, port, clientAddress, logicalDevice, timeoutMs);
        driverObject.log(DriverLogLevel.INFO, "DLMS associated with " + host + ":" + port);
    }

    @Override
    public void disconnect() {
        if (communicator != null) {
            communicator.close();
            communicator = null;
        }
    }

    @Override
    public boolean isConnected() {
        return communicator != null && communicator.isOpen();
    }

    @Override
    public void readPoints(Map<String, String> pointMappings) throws DriverException {
        if (!isConnected()) {
            throw new DriverException("Not connected");
        }
        points.clear();
        for (Map.Entry<String, String> entry : pointMappings.entrySet()) {
            DlmsPoint point = DlmsPoint.parse(entry.getValue());
            points.put(entry.getKey(), point);
            try {
                driverObject.updateVariable(entry.getKey(), readPoint(point));
            } catch (DriverException ex) {
                driverObject.updateVariable(entry.getKey(), unavailableRecord(point));
                driverObject.log(DriverLogLevel.DEBUG, "DLMS read skipped for " + entry.getKey() + ": " + ex.getMessage());
            }
        }
    }

    @Override
    public void writePoint(String pointId, DataRecord value) throws DriverException {
        if (!isConnected()) {
            throw new DriverException("Not connected");
        }
        DlmsPoint point = points.get(pointId);
        if (point == null) {
            throw new DriverException("Unknown point: " + pointId);
        }
        communicator.writeAttribute(point, value);
        driverObject.updateVariable(pointId, readPoint(point));
    }

    private DataRecord readPoint(DlmsPoint point) throws DriverException {
        Object raw = communicator.readAttribute(point);
        Object formatted = DlmsValueCodec.formatReadValue(raw);
        return DataRecord.single(VALUE_SCHEMA, Map.of(
                "value", formatted == null ? "" : String.valueOf(formatted),
                "obis", point.obis(),
                "logicalDevice", point.logicalDevice(),
                "objectType", point.objectType().name(),
                "attributeIndex", point.attributeIndex(),
                "quality", "GOOD"
        ));
    }

    private static DataRecord unavailableRecord(DlmsPoint point) {
        return DataRecord.single(VALUE_SCHEMA, Map.of(
                "value", "",
                "obis", point.obis(),
                "logicalDevice", point.logicalDevice(),
                "objectType", point.objectType().name(),
                "attributeIndex", point.attributeIndex(),
                "quality", "UNAVAILABLE"
        ));
    }

    private void readConfig(String name, java.util.function.Consumer<String> consumer) {
        driverObject.getVariable(name).ifPresent(record -> {
            Object raw = record.firstRow().get("raw");
            if (raw == null) {
                raw = record.firstRow().get("value");
            }
            if (raw != null) {
                consumer.accept(raw.toString());
            }
        });
    }
}
