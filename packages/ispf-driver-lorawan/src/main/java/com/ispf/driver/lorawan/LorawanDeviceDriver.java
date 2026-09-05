package com.ispf.driver.lorawan;

import com.ispf.core.model.DataRecord;
import com.ispf.core.model.DataSchema;
import com.ispf.core.model.FieldType;
import com.ispf.driver.DeviceDriver;
import com.ispf.driver.DriverException;
import com.ispf.driver.DriverMetadata;
import com.ispf.driver.lorawan.codec.LorawanLabCodec;
import com.ispf.driver.lorawan.codec.LorawanLabSession;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * LoRaWAN NS/AS (or packet-forwarder–shaped) TCP gateway lab driver.
 * <p>
 * Point mapping is a DevEUI ({@code AABBCCDDEEFF0011}, {@code deveui:…}).
 * {@code readPoints} polls last uplink JSON; {@code writePoint} sends a downlink via
 * {@code session.writeValue(...)}.
 * <p>
 * Honesty: NS/AS or packet-forwarder gateway lab — not LoRa PHY / Semtech HAL.
 * Clean-room ISPF code, Apache-2.0 — JDK sockets only.
 */
public class LorawanDeviceDriver implements DeviceDriver {

    private static final DataSchema VALUE_SCHEMA = DataSchema.builder("lorawanValue")
            .field("value", FieldType.DOUBLE)
            .field("deveui", FieldType.STRING)
            .field("rssi", FieldType.DOUBLE)
            .field("raw", FieldType.STRING)
            .build();

    private static final DriverMetadata METADATA = new DriverMetadata(
            "lorawan",
            "LoRaWAN NS/AS Gateway Lab Driver",
            "0.1.0",
            "LoRaWAN NS/AS or packet-forwarder gateway lab: TCP JSON GET uplink / TX downlink on 1700;"
                    + " not LoRa PHY / Semtech HAL",
            "ISPF",
            Map.of(
                    "host", "127.0.0.1",
                    "port", "1700",
                    "timeoutMs", "3000"
            ),
            null,
            Set.of("read", "write")
    );

    private DriverObject driverObject;
    private String host = "127.0.0.1";
    private int port = 1700;
    private int timeoutMs = 3000;
    private LorawanLabSession session;
    private final Map<String, LorawanPoint> points = new ConcurrentHashMap<>();

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
            case "timeoutMs" -> timeoutMs = Integer.parseInt(value.trim());
            default -> { }
        }
    }

    @Override
    public void connect() throws DriverException {
        disconnect();
        try {
            session = new LorawanLabSession(host, port, timeoutMs);
            driverObject.log(DriverLogLevel.INFO,
                    "LoRaWAN NS/AS gateway lab connected to " + host + ":" + port
                            + " (not LoRa PHY / Semtech HAL)");
        } catch (IOException e) {
            session = null;
            throw new DriverException("LoRaWAN lab connect failed for " + host + ":" + port, e);
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
            LorawanPoint point = LorawanPoint.parse(mapping);
            points.put(entry.getKey(), point);
            try {
                LorawanLabCodec.Uplink uplink = session.readUplink(point.deveui());
                String deveui = uplink.deveui().isBlank() ? point.deveui() : uplink.deveui();
                driverObject.updateVariable(entry.getKey(), DataRecord.single(VALUE_SCHEMA, Map.of(
                        "value", (double) uplink.value(),
                        "deveui", deveui,
                        "rssi", uplink.rssi(),
                        "raw", uplink.raw()
                )));
            } catch (IOException e) {
                throw new DriverException("LoRaWAN lab read failed for " + mapping, e);
            }
        }
    }

    @Override
    public void writePoint(String pointId, DataRecord value) throws DriverException {
        ensureConnected();
        LorawanPoint point = points.get(pointId);
        if (point == null) {
            throw new DriverException("Unknown point: " + pointId);
        }
        float numeric = (float) extractNumeric(value);
        try {
            session.writeValue(point.deveui(), numeric);
            driverObject.updateVariable(pointId, DataRecord.single(VALUE_SCHEMA, Map.of(
                    "value", (double) numeric,
                    "deveui", point.deveui(),
                    "rssi", 0.0,
                    "raw", "TX"
            )));
        } catch (IOException e) {
            throw new DriverException("LoRaWAN lab write failed for " + pointId, e);
        }
    }

    private static double extractNumeric(DataRecord value) {
        if (value == null || value.rowCount() == 0) {
            throw new IllegalArgumentException("LoRaWAN write requires a value");
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
        throw new IllegalArgumentException("LoRaWAN write requires numeric value/raw");
    }

    private void ensureConnected() throws DriverException {
        if (!isConnected()) {
            throw new DriverException("Not connected");
        }
    }
}
