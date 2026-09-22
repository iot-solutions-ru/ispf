package com.ispf.driver.bacnetmstp;

import com.ispf.core.model.DataRecord;
import com.ispf.core.model.DataSchema;
import com.ispf.core.model.FieldType;
import com.ispf.driver.DeviceDriver;
import com.ispf.driver.DriverException;
import com.ispf.driver.DriverMetadata;
import com.ispf.driver.bacnetmstp.codec.BacnetMstpSession;

import java.io.IOException;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * BACnet MS/TP driver over a TCP serial-server ({@code bacnet-mstp}).
 * <p>
 * Speaks ASHRAE 135 clause-9 frames (preamble {@code 55 FF}, header CRC-8, data CRC-16)
 * carrying confirmed ReadProperty / WriteProperty APDUs. Not a native RS-485 MS/TP
 * token-passing master. Point forms: {@code analog-input,1}, {@code AI:1}, {@code AO:2},
 * {@code AV:3}. Reads present-value; writes allowed for AO/AV.
 * <p>
 * Clean-room ISPF code, Apache-2.0 — JDK sockets only.
 */
public class BacnetMstpDeviceDriver implements DeviceDriver {

    private static final DataSchema VALUE_SCHEMA = DataSchema.builder("bacnetMstpValue")
            .field("value", FieldType.DOUBLE)
            .field("objectType", FieldType.STRING)
            .field("instance", FieldType.LONG)
            .field("property", FieldType.STRING)
            .build();

    private static final DriverMetadata METADATA = new DriverMetadata(
            "bacnet-mstp",
            "BACnet MS/TP TCP Driver",
            "1.0.0",
            "BACnet MS/TP clause-9 over TCP serial-server: frame type 5 Read/WriteProperty;"
                    + " not native RS-485 MS/TP master",
            "ISPF",
            Map.of(
                    "host", "127.0.0.1",
                    "port", "47808",
                    "localMac", "0",
                    "remoteMac", "1",
                    "timeoutMs", "3000"
            ),
            null,
            Set.of("read", "write")
    );

    private DriverObject driverObject;
    private String host = "127.0.0.1";
    private int port = 47808;
    private int localMac = 0;
    private int remoteMac = 1;
    private int timeoutMs = 3000;
    private BacnetMstpSession session;
    private final Map<String, BacnetMstpPoint> points = new ConcurrentHashMap<>();

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
            case "host" -> host = value.trim();
            case "port" -> port = Integer.parseInt(value.trim());
            case "localMac" -> localMac = Integer.parseInt(value.trim());
            case "remoteMac" -> remoteMac = Integer.parseInt(value.trim());
            case "timeoutMs" -> timeoutMs = Integer.parseInt(value.trim());
            default -> { }
        }
    }

    @Override
    public void connect() throws DriverException {
        disconnect();
        try {
            session = new BacnetMstpSession(host, port, localMac, remoteMac, timeoutMs);
            driverObject.log(DriverLogLevel.INFO,
                    "BACnet MS/TP connected to " + host + ":" + port);
        } catch (IOException e) {
            session = null;
            throw new DriverException("BACnet MS/TP connect failed for " + host + ":" + port, e);
        }
    }

    @Override
    public void disconnect() {
        if (session != null) {
            session.close();
            session = null;
        }
        points.clear();
    }

    @Override
    public boolean isConnected() {
        return session != null && session.isConnected();
    }

    @Override
    public void readPoints(Map<String, String> pointMappings) throws DriverException {
        ensureConnected();
        points.clear();
        for (Map.Entry<String, String> entry : pointMappings.entrySet()) {
            String mapping = entry.getValue() == null || entry.getValue().isBlank()
                    ? entry.getKey() : entry.getValue();
            BacnetMstpPoint point = BacnetMstpPoint.parse(mapping);
            points.put(entry.getKey(), point);
            try {
                float value = session.readPresentValue(point.encodedObjectId());
                driverObject.updateVariable(entry.getKey(), toRecord(point, value));
            } catch (IOException e) {
                throw new DriverException("BACnet MS/TP read failed for " + mapping, e);
            }
        }
    }

    @Override
    public void writePoint(String pointId, DataRecord value) throws DriverException {
        ensureConnected();
        BacnetMstpPoint point = points.get(pointId);
        if (point == null) {
            throw new DriverException("Unknown point: " + pointId);
        }
        if (!point.objectType().writable) {
            throw new DriverException(
                    "BACnet MS/TP rejects writes for object type: " + point.objectType());
        }
        float numeric = (float) extractNumeric(value);
        try {
            session.writeValue(point.encodedObjectId(), numeric);
            driverObject.updateVariable(pointId, toRecord(point, numeric));
        } catch (IOException e) {
            throw new DriverException("BACnet MS/TP write failed for " + pointId, e);
        }
    }

    private static DataRecord toRecord(BacnetMstpPoint point, float value) {
        return DataRecord.single(VALUE_SCHEMA, Map.of(
                "value", (double) value,
                "objectType", point.objectType().name().toLowerCase(Locale.ROOT).replace('_', '-'),
                "instance", (long) point.instance(),
                "property", "present-value"
        ));
    }

    private static double extractNumeric(DataRecord value) {
        if (value == null || value.rowCount() == 0) {
            throw new IllegalArgumentException("BACnet MS/TP write requires a value");
        }
        Map<String, Object> row = value.firstRow();
        for (String key : List.of("value", "raw")) {
            Object candidate = row.get(key);
            if (candidate instanceof Number number) {
                return number.doubleValue();
            }
            if (candidate != null) {
                return Double.parseDouble(String.valueOf(candidate).trim());
            }
        }
        throw new IllegalArgumentException("BACnet MS/TP write requires numeric value/raw");
    }

    private void ensureConnected() throws DriverException {
        if (!isConnected()) {
            throw new DriverException("Not connected");
        }
    }
}
