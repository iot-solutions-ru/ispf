package com.ispf.driver.zigbee;

import com.ispf.core.model.DataRecord;
import com.ispf.core.model.DataSchema;
import com.ispf.core.model.FieldType;
import com.ispf.driver.DeviceDriver;
import com.ispf.driver.DriverException;
import com.ispf.driver.DriverMaturity;
import com.ispf.driver.DriverMetadata;
import com.ispf.driver.zigbee.codec.AshCodec;
import com.ispf.driver.zigbee.codec.AshSession;

import java.io.IOException;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Silicon Labs ASH (EZSP UART) driver over TCP (default port {@code 17754}).
 * <p>
 * Not 802.15.4 and not a ZCL stack. Connect sends host RST {@code 1A C0 38 BC 7E};
 * peer replies with RSTACK. Point forms: {@code version}, {@code reason}.
 * <p>
 * Clean-room ISPF code, Apache-2.0 — JDK sockets only.
 */
public class ZigbeeDeviceDriver implements DeviceDriver {

    private static final DataSchema VALUE_SCHEMA = DataSchema.builder("zigbeeAshValue")
            .field("value", FieldType.DOUBLE)
            .field("kind", FieldType.STRING)
            .field("point", FieldType.STRING)
            .build();

    private static final DriverMetadata METADATA = new DriverMetadata(
            "zigbee",
            "Zigbee ASH EZSP UART Driver",
            "1.0.0",
            "ASH (EZSP UART) over TCP;"
                    + " not 802.15.4 and not a ZCL stack",
            "ISPF",
            Map.of(
                    "host", "127.0.0.1",
                    "port", "17754",
                    "timeoutMs", "3000"
            ),
            DriverMaturity.BETA,
            Set.of("read")
    );

    private DriverObject driverObject;
    private String host = "127.0.0.1";
    private int port = 17754;
    private int timeoutMs = 3000;
    private AshSession session;
    private final Map<String, ZigbeePoint> points = new ConcurrentHashMap<>();

    @Override
    public DriverMetadata metadata() {
        return METADATA;
    }

    /** Host RST frame produced by {@link AshCodec#encodeHostReset()}. */
    public static byte[] hostResetFrame() {
        return AshCodec.encodeHostReset();
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
            session = new AshSession(host, port, timeoutMs);
            driverObject.log(DriverLogLevel.INFO,
                    "Zigbee ASH (EZSP UART) connected to " + host + ":" + port
                            + " (not 802.15.4 / not ZCL)");
        } catch (IOException e) {
            session = null;
            throw new DriverException(
                    "Zigbee ASH connect failed for " + host + ":" + port, e);
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
        AshCodec.AshRstack rstack;
        try {
            rstack = session.reset();
        } catch (IOException e) {
            throw new DriverException("Zigbee ASH RSTACK read failed", e);
        }
        for (Map.Entry<String, String> entry : pointMappings.entrySet()) {
            String mapping = entry.getValue() == null || entry.getValue().isBlank()
                    ? entry.getKey() : entry.getValue();
            ZigbeePoint point = ZigbeePoint.parse(mapping);
            points.put(entry.getKey(), point);
            double value = point.kind() == ZigbeePoint.Kind.VERSION
                    ? rstack.version()
                    : rstack.reason();
            driverObject.updateVariable(entry.getKey(), toRecord(point, value));
        }
    }

    @Override
    public void writePoint(String pointId, DataRecord value) throws DriverException {
        throw new DriverException(
                "Zigbee ASH (EZSP UART) is read-only (RSTACK version/reason); point="
                        + pointId.toLowerCase(Locale.ROOT));
    }

    private static DataRecord toRecord(ZigbeePoint point, double value) {
        return DataRecord.single(VALUE_SCHEMA, Map.of(
                "value", value,
                "kind", point.kind() == ZigbeePoint.Kind.VERSION ? "version" : "reason",
                "point", point.display()
        ));
    }

    private void ensureConnected() throws DriverException {
        if (!isConnected()) {
            throw new DriverException("Not connected");
        }
    }
}
