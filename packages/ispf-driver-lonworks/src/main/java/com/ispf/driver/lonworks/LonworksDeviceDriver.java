package com.ispf.driver.lonworks;

import com.ispf.core.model.DataRecord;
import com.ispf.core.model.DataSchema;
import com.ispf.core.model.FieldType;
import com.ispf.driver.DeviceDriver;
import com.ispf.driver.DriverException;
import com.ispf.driver.DriverMetadata;
import com.ispf.driver.lonworks.codec.LonworksLabSession;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * LonWorks LonTalk-IP / LON-over-TCP gateway lab driver — ASCII GET/SET over TCP (not native TP).
 * <p>
 * Point forms: {@code nviTemp}, {@code nvoSetpoint}, {@code nvi:temp}, {@code nv:1}.
 * Speaks to a LonTalk-IP gateway lab on {@code host:port} (default 1628). Honesty: gateway lab
 * only — not a native twisted-pair LonTalk master and not an Echelon/Adesto stack.
 * Clean-room ISPF code, Apache-2.0 — JDK sockets only.
 */
public class LonworksDeviceDriver implements DeviceDriver {

    private static final DataSchema VALUE_SCHEMA = DataSchema.builder("lonworksValue")
            .field("value", FieldType.DOUBLE)
            .field("nv", FieldType.STRING)
            .build();

    private static final DriverMetadata METADATA = new DriverMetadata(
            "lonworks",
            "LonWorks LonTalk-IP Gateway Lab Driver",
            "0.1.0",
            "LonWorks LonTalk-IP / LON-over-TCP gateway lab: ASCII GET/SET network variables;"
                    + " not native twisted-pair LonTalk master, not Echelon/Adesto stack",
            "ISPF",
            Map.of(
                    "host", "127.0.0.1",
                    "port", "1628",
                    "timeoutMs", "3000"
            ),
            null,
            Set.of("read", "write")
    );

    private DriverObject driverObject;
    private String host = "127.0.0.1";
    private int port = 1628;
    private int timeoutMs = 3000;
    private LonworksLabSession session;
    private final Map<String, LonworksPoint> points = new ConcurrentHashMap<>();

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
            session = new LonworksLabSession(host, port, timeoutMs);
            driverObject.log(DriverLogLevel.INFO,
                    "LonWorks LonTalk-IP gateway lab connected to " + host + ":" + port);
        } catch (IOException e) {
            session = null;
            throw new DriverException("LonWorks gateway connect failed for " + host + ":" + port, e);
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
            LonworksPoint point = LonworksPoint.parse(mapping);
            points.put(entry.getKey(), point);
            try {
                float value = session.readValue(point.gatewayToken());
                driverObject.updateVariable(entry.getKey(), toRecord(point, value));
            } catch (IOException e) {
                throw new DriverException("LonWorks gateway read failed for " + mapping, e);
            }
        }
    }

    @Override
    public void writePoint(String pointId, DataRecord value) throws DriverException {
        ensureConnected();
        LonworksPoint point = points.get(pointId);
        if (point == null) {
            throw new DriverException("Unknown point: " + pointId);
        }
        float numeric = (float) extractNumeric(value);
        try {
            session.writeValue(point.gatewayToken(), numeric);
            driverObject.updateVariable(pointId, toRecord(point, numeric));
        } catch (IOException e) {
            throw new DriverException("LonWorks gateway write failed for " + pointId, e);
        }
    }

    private static DataRecord toRecord(LonworksPoint point, float value) {
        return DataRecord.single(VALUE_SCHEMA, Map.of(
                "value", (double) value,
                "nv", point.gatewayToken()
        ));
    }

    private static double extractNumeric(DataRecord value) {
        if (value == null || value.rowCount() == 0) {
            throw new IllegalArgumentException("LonWorks write requires a value");
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
        throw new IllegalArgumentException("LonWorks write requires numeric value/raw");
    }

    private void ensureConnected() throws DriverException {
        if (!isConnected()) {
            throw new DriverException("Not connected");
        }
    }
}
