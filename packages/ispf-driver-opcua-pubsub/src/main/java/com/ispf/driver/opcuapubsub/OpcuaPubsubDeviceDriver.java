package com.ispf.driver.opcuapubsub;

import com.ispf.core.model.DataRecord;
import com.ispf.core.model.DataSchema;
import com.ispf.core.model.FieldType;
import com.ispf.driver.DeviceDriver;
import com.ispf.driver.DriverException;
import com.ispf.driver.DriverMetadata;
import com.ispf.driver.opcuapubsub.codec.OpcuaPubsubLabSession;

import java.io.IOException;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * OPC UA PubSub UADP/UDP lab driver — minimal publisher/subscriber subset (default port {@code 4840}).
 * <p>
 * Point forms: {@code ds:1}, {@code field:0}, {@code ns:2;s=Temp}.
 * Reads issue a GET datagram and expect a SAMPLE response (request/response UDP for testability).
 * Writes PUBLISH a lab sample (float/double/string payload) and expect ACK.
 * <p>
 * Honesty: UADP/UDP lab subset — not full OPC UA PubSub / MQTT / broker / security. Lab ≠ field.
 * Clean-room ISPF code, Apache-2.0 — JDK sockets only.
 */
public class OpcuaPubsubDeviceDriver implements DeviceDriver {

    private static final DataSchema VALUE_SCHEMA = DataSchema.builder("opcuaPubsubValue")
            .field("value", FieldType.DOUBLE)
            .field("kind", FieldType.STRING)
            .field("point", FieldType.STRING)
            .build();

    private static final DriverMetadata METADATA = new DriverMetadata(
            "opcua-pubsub",
            "OPC UA PubSub UADP/UDP Lab Driver",
            "0.1.0",
            "UADP/UDP lab subset (GET/SAMPLE/PUBLISH dataset payload) —"
                    + " not full OPC UA PubSub / MQTT / broker / security",
            "ISPF",
            Map.of(
                    "host", "127.0.0.1",
                    "port", "4840",
                    "timeoutMs", "3000"
            ),
            null,
            Set.of("read", "write")
    );

    private DriverObject driverObject;
    private String host = "127.0.0.1";
    private int port = 4840;
    private int timeoutMs = 3000;
    private OpcuaPubsubLabSession session;
    private final Map<String, OpcuaPubsubPoint> points = new ConcurrentHashMap<>();

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
            session = new OpcuaPubsubLabSession(host, port, timeoutMs);
            driverObject.log(DriverLogLevel.INFO,
                    "OPC UA PubSub UADP/UDP lab connected to " + host + ":" + port
                            + " (lab subset — not full PubSub / MQTT / broker / security)");
        } catch (IOException e) {
            session = null;
            throw new DriverException("OPC UA PubSub lab connect failed for " + host + ":" + port, e);
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
            OpcuaPubsubPoint point = OpcuaPubsubPoint.parse(mapping);
            points.put(entry.getKey(), point);
            try {
                double value = session.readValue(point.wireToken());
                driverObject.updateVariable(entry.getKey(), toRecord(point, value));
            } catch (IOException e) {
                throw new DriverException("OPC UA PubSub lab read failed for " + mapping, e);
            }
        }
    }

    @Override
    public void writePoint(String pointId, DataRecord value) throws DriverException {
        ensureConnected();
        OpcuaPubsubPoint point = points.get(pointId);
        if (point == null) {
            throw new DriverException("Unknown point: " + pointId + " (read it first)");
        }
        double numeric = extractNumeric(value);
        try {
            session.publishSample(point.wireToken(), numeric);
            driverObject.updateVariable(pointId, toRecord(point, numeric));
        } catch (IOException e) {
            throw new DriverException("OPC UA PubSub lab write failed for " + pointId, e);
        }
    }

    private static DataRecord toRecord(OpcuaPubsubPoint point, double value) {
        return DataRecord.single(VALUE_SCHEMA, Map.of(
                "value", value,
                "kind", point.kind().name().toLowerCase(Locale.ROOT),
                "point", point.display()
        ));
    }

    private static double extractNumeric(DataRecord value) {
        if (value == null || value.rowCount() == 0) {
            throw new IllegalArgumentException("OPC UA PubSub write requires a value");
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
        throw new IllegalArgumentException("OPC UA PubSub write requires numeric value/raw");
    }

    private void ensureConnected() throws DriverException {
        if (!isConnected()) {
            throw new DriverException("Not connected");
        }
    }
}
