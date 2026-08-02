package com.ispf.driver.bacnet;

import com.ispf.core.model.DataRecord;
import com.ispf.core.model.DataSchema;
import com.ispf.core.model.FieldType;
import com.ispf.driver.DeviceDriver;
import com.ispf.driver.DriverException;
import com.ispf.driver.DriverMetadata;
import com.ispf.driver.DriverPollTimestamps;
import com.ispf.driver.bacnet.codec.BacnetEngineeringUnit;
import com.ispf.driver.bacnet.codec.BacnetException;
import com.ispf.driver.bacnet.codec.BacnetIpClient;
import com.ispf.driver.bacnet.codec.BacnetObjectIdentifier;
import com.ispf.driver.bacnet.codec.BacnetPropertyIdentifier;
import com.ispf.driver.bacnet.codec.BacnetValue;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * BACnet/IP driver backed by the ISPF clean-room UDP codec.
 * <p>
 * Point mapping: {@code objectType:instance:property} e.g. {@code analog-input:1:present-value}.
 */
public class BacnetDeviceDriver implements DeviceDriver {

    private static final DriverMetadata METADATA = new DriverMetadata(
            "bacnet",
            "BACnet/IP Driver",
            "0.1.0",
            "Polls BACnet/IP devices and maps object properties to ISPF variables",
            "ISPF",
            Map.of(
                    "host", "127.0.0.1",
                    "port", "47808",
                    "localDeviceId", "1234",
                    "remoteDeviceId", "1001",
                    "discoveryMode", "static",
                    "timeoutMs", "5000",
                    "pollIntervalMs", "5000"
            )
    );

    private static final DataSchema VALUE_SCHEMA = DataSchema.builder("bacnetValue")
            .field("value", FieldType.STRING)
            .field("property", FieldType.STRING)
            .field("unit", FieldType.STRING)
            .build();

    private DriverObject driverObject;
    private BacnetIpClient client;
    private String host = "127.0.0.1";
    private String bindAddress = "0.0.0.0";
    private int port = 47808;
    private int localDeviceId = 1234;
    private int remoteDeviceId = 1001;
    private String discoveryMode = "static";
    private int timeoutMs = 5000;
    private int bindPort = -1;
    private final Map<String, BacnetPoint> points = new ConcurrentHashMap<>();
    private final Map<String, String> unitCache = new ConcurrentHashMap<>();
    private volatile boolean connected;

    @Override
    public DriverMetadata metadata() {
        return METADATA;
    }

    @Override
    public void initialize(DriverObject driverObject) {
        this.driverObject = driverObject;
        readConfig("host", value -> host = value);
        readConfig("bindAddress", value -> bindAddress = value.trim());
        readConfig("port", value -> port = Integer.parseInt(value));
        readConfig("localDeviceId", value -> localDeviceId = Integer.parseInt(value));
        readConfig("remoteDeviceId", value -> remoteDeviceId = Integer.parseInt(value));
        readConfig("discoveryMode", value -> discoveryMode = value.trim());
        readConfig("timeoutMs", value -> timeoutMs = Integer.parseInt(value));
        readConfig("bindPort", value -> bindPort = Integer.parseInt(value));
    }

    @Override
    public void connect() throws DriverException {
        try {
            client = new BacnetIpClient(bindAddress, bindPort, host, port, timeoutMs);
            if (isWhoIsDiscovery()) {
                client.discoverRemoteDevice(remoteDeviceId);
                driverObject.log(
                        DriverLogLevel.INFO,
                        "Discovered BACnet device " + remoteDeviceId + " via Who-Is"
                );
            } else {
                client.connectStatic(remoteDeviceId);
                driverObject.log(
                        DriverLogLevel.INFO,
                        "Connected to BACnet device " + remoteDeviceId + " at " + host + ":" + port
                );
            }
            connected = true;
        } catch (Exception e) {
            connected = false;
            closeClient();
            throw new DriverException("BACnet connect failed for local device " + localDeviceId, e);
        }
    }

    @Override
    public void disconnect() {
        connected = false;
        closeClient();
    }

    @Override
    public boolean isConnected() {
        return connected && client != null && client.isConnected();
    }

    @Override
    public void readPoints(Map<String, String> pointMappings) throws DriverException {
        if (!isConnected()) {
            throw new DriverException("Not connected");
        }
        points.clear();
        Instant observedAt = DriverPollTimestamps.pollTick();
        for (Map.Entry<String, String> entry : pointMappings.entrySet()) {
            BacnetPoint point = BacnetPoint.parse(entry.getValue());
            points.put(entry.getKey(), point);
            driverObject.updateVariable(entry.getKey(), readPoint(point), observedAt);
        }
    }

    @Override
    public void writePoint(String pointId, DataRecord value) throws DriverException {
        if (!isConnected()) {
            throw new DriverException("Not connected");
        }
        BacnetPoint point = points.get(pointId);
        if (point == null) {
            throw new DriverException("Unknown point: " + pointId);
        }
        if (!isWritable(point)) {
            throw new DriverException("BACnet object type is read-only: " + point.objectType());
        }
        try {
            BacnetObjectIdentifier objectId = new BacnetObjectIdentifier(point.objectType(), point.instance());
            client.writeProperty(objectId, point.property(), encodeWriteValue(point, value));
            driverObject.updateVariable(pointId, readPoint(point), DriverPollTimestamps.pollTick());
        } catch (BacnetException e) {
            throw new DriverException("BACnet write failed for point " + pointId, e);
        }
    }

    private static boolean isWritable(BacnetPoint point) {
        return point.objectType().isWritable() && point.property() == BacnetPropertyIdentifier.PRESENT_VALUE;
    }

    private static BacnetValue encodeWriteValue(BacnetPoint point, DataRecord value) throws DriverException {
        Object raw = value.firstRow().get("raw");
        if (raw == null) {
            raw = value.firstRow().get("value");
        }
        if (raw == null) {
            throw new DriverException("BACnet write requires value or raw field");
        }
        if (point.objectType().isAnalog()) {
            return new BacnetValue.RealValue((float) extractDouble(raw));
        }
        if (point.objectType().isBinary()) {
            return new BacnetValue.BinaryValue(extractBoolean(raw));
        }
        throw new DriverException("Unsupported BACnet write type: " + point.objectType());
    }

    private static double extractDouble(Object raw) throws DriverException {
        if (raw instanceof Number number) {
            return number.doubleValue();
        }
        try {
            return Double.parseDouble(String.valueOf(raw));
        } catch (NumberFormatException e) {
            throw new DriverException("BACnet write requires numeric value: " + raw, e);
        }
    }

    private static boolean extractBoolean(Object raw) {
        if (raw instanceof Boolean bool) {
            return bool;
        }
        String text = String.valueOf(raw).trim().toLowerCase();
        return "true".equals(text) || "1".equals(text) || "on".equals(text) || "active".equals(text);
    }

    private DataRecord readPoint(BacnetPoint point) throws DriverException {
        try {
            BacnetObjectIdentifier objectId = new BacnetObjectIdentifier(point.objectType(), point.instance());
            BacnetValue rawValue = client.readProperty(objectId, point.property());
            Map<String, Object> fields = new LinkedHashMap<>();
            fields.put("value", BacnetValueDecoder.formatValue(rawValue, point.objectType()));
            fields.put("property", point.property().protocolName());
            if (point.property() == BacnetPropertyIdentifier.PRESENT_VALUE
                    && BacnetValueDecoder.supportsUnitMetadata(point.objectType())) {
                String unit = readUnit(objectId);
                if (unit != null && !unit.isBlank()) {
                    fields.put("unit", unit);
                }
            }
            return DataRecord.single(VALUE_SCHEMA, fields);
        } catch (BacnetException e) {
            throw new DriverException("BACnet read failed for " + point, e);
        }
    }

    private String readUnit(BacnetObjectIdentifier objectId) {
        String cacheKey = objectId.toString();
        return unitCache.computeIfAbsent(cacheKey, key -> {
            try {
                BacnetValue rawUnits = client.readProperty(objectId, BacnetPropertyIdentifier.UNITS);
                if (rawUnits instanceof BacnetValue.UnsignedValue units) {
                    return BacnetEngineeringUnits.toHaystackUnit(BacnetEngineeringUnit.fromId(units.value()));
                }
                return "";
            } catch (BacnetException e) {
                return "";
            }
        });
    }

    private boolean isWhoIsDiscovery() {
        return "whoIs".equalsIgnoreCase(discoveryMode) || "who-is".equalsIgnoreCase(discoveryMode);
    }

    private void closeClient() {
        if (client != null) {
            client.close();
            client = null;
        }
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
