package com.ispf.driver.wmbus;

import com.ispf.core.model.DataRecord;
import com.ispf.core.model.DataSchema;
import com.ispf.core.model.FieldType;
import com.ispf.driver.DeviceDriver;
import com.ispf.driver.DriverException;
import com.ispf.driver.DriverMetadata;
import com.ispf.driver.wmbus.codec.WmbusCodec;
import com.ispf.driver.wmbus.codec.WmbusSession;

import java.io.IOException;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Wireless M-Bus (OMS) driver over a TCP serial-server ({@code wmbus}).
 * <p>
 * Speaks EN 13757-4 Format-A link frames with CRC-16/EN-13757 and CI {@code 0x7A}
 * application payloads. Not an RF PHY. Points: {@code meter:1}, {@code id:HEX}.
 * Read-only: writes throw a clear exception.
 * <p>
 * Clean-room ISPF code, Apache-2.0 — JDK sockets only.
 */
public class WmbusDeviceDriver implements DeviceDriver {

    private static final DataSchema VALUE_SCHEMA = DataSchema.builder("wmbusValue")
            .field("value", FieldType.DOUBLE)
            .field("deviceId", FieldType.STRING)
            .field("ci", FieldType.LONG)
            .field("telegram", FieldType.STRING)
            .build();

    private static final DriverMetadata METADATA = new DriverMetadata(
            "wmbus",
            "Wireless M-Bus TCP Driver",
            "1.0.0",
            "Wireless M-Bus EN 13757-4 Format-A over TCP serial-server: CRC-16/EN-13757,"
                    + " CI 0x7A; not RF PHY",
            "ISPF",
            Map.of(
                    "host", "127.0.0.1",
                    "port", "10000",
                    "timeoutMs", "3000"
            ),
            null,
            Set.of("read")
    );

    private DriverObject driverObject;
    private String host = "127.0.0.1";
    private int port = 10000;
    private int timeoutMs = 3000;
    private WmbusSession session;
    private final Map<String, WmbusPoint> points = new ConcurrentHashMap<>();

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
            session = new WmbusSession(host, port, timeoutMs);
            driverObject.log(DriverLogLevel.INFO, "wM-Bus connected to " + host + ":" + port);
        } catch (IOException e) {
            session = null;
            throw new DriverException("wM-Bus connect failed for " + host + ":" + port, e);
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
            WmbusPoint point = WmbusPoint.parse(mapping);
            points.put(entry.getKey(), point);
            try {
                WmbusCodec.ParsedTelegram telegram = switch (point.kind()) {
                    case METER_INDEX -> session.pollMeter(Integer.parseInt(point.key()));
                    case DEVICE_ID -> session.pollDeviceId(point.key());
                };
                byte[] raw = WmbusCodec.encodeTelegram(
                        telegram.manufacturer(),
                        telegram.deviceId(),
                        telegram.version(),
                        telegram.deviceType(),
                        telegram.value()
                );
                driverObject.updateVariable(entry.getKey(), DataRecord.single(VALUE_SCHEMA, Map.of(
                        "value", (double) telegram.value(),
                        "deviceId", WmbusCodec.deviceIdHex(telegram.deviceId()),
                        "ci", (long) telegram.ci(),
                        "telegram", WmbusCodec.toHex(raw)
                )));
            } catch (IOException e) {
                throw new DriverException("wM-Bus read failed for " + mapping, e);
            }
        }
    }

    @Override
    public void writePoint(String pointId, DataRecord value) throws DriverException {
        throw new DriverException("Wireless M-Bus is read-only (telegram ingest only)");
    }

    private void ensureConnected() throws DriverException {
        if (!isConnected()) {
            throw new DriverException("Not connected");
        }
    }
}
