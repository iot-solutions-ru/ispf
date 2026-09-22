package com.ispf.driver.ethernetpowerlink;

import com.ispf.core.model.DataRecord;
import com.ispf.core.model.DataSchema;
import com.ispf.core.model.FieldType;
import com.ispf.driver.DeviceDriver;
import com.ispf.driver.DriverException;
import com.ispf.driver.DriverMaturity;
import com.ispf.driver.DriverMetadata;
import com.ispf.driver.ethernetpowerlink.codec.EthernetPowerlinkSession;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Ethernet POWERLINK frame driver over a TCP gateway ({@code ethernet-powerlink}).
 * <p>
 * Sends basic POWERLINK headers (SoC/PReq/PRes) with destination and source node IDs.
 * Not a hard real-time Managing Node.
 * <p>
 * Point forms: {@code node:1:obj:0x6000:01}, {@code pdo:1}.
 */
public class EthernetPowerlinkDeviceDriver implements DeviceDriver {

    private static final DataSchema VALUE_SCHEMA = DataSchema.builder("ethernetPowerlinkValue")
            .field("value", FieldType.DOUBLE)
            .field("kind", FieldType.STRING)
            .field("point", FieldType.STRING)
            .build();

    private static final DriverMetadata METADATA = new DriverMetadata(
            "ethernet-powerlink",
            "Ethernet POWERLINK Driver",
            "1.0.0",
            "POWERLINK frame over TCP gateway; not a hard MN",
            "ISPF",
            Map.of(
                    "host", "127.0.0.1",
                    "port", "6040",
                    "timeoutMs", "3000"
            ),
            DriverMaturity.BETA,
            Set.of("read", "write")
    );

    private DriverObject driverObject;
    private String host = "127.0.0.1";
    private int port = 6040;
    private int timeoutMs = 3000;
    private EthernetPowerlinkSession session;
    private final Map<String, EthernetPowerlinkPoint> points = new ConcurrentHashMap<>();

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
            session = new EthernetPowerlinkSession(host, port, timeoutMs);
            driverObject.log(DriverLogLevel.INFO,
                    "POWERLINK connected to " + host + ":" + port + " (TCP gateway, not hard MN)");
        } catch (IOException e) {
            session = null;
            throw new DriverException(
                    "POWERLINK connect failed for " + host + ":" + port, e);
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
            EthernetPowerlinkPoint point = EthernetPowerlinkPoint.parse(mapping);
            points.put(entry.getKey(), point);
            try {
                double value = session.readValue(point.destinationNode());
                driverObject.updateVariable(entry.getKey(), toRecord(point, value));
            } catch (IOException e) {
                throw new DriverException("POWERLINK read failed for " + mapping, e);
            }
        }
    }

    @Override
    public void writePoint(String pointId, DataRecord value) throws DriverException {
        ensureConnected();
        EthernetPowerlinkPoint point = points.get(pointId);
        if (point == null) {
            throw new DriverException("Unknown point: " + pointId + " (read it first)");
        }
        double numeric = extractNumeric(value);
        try {
            session.writeValue(point.destinationNode(), numeric);
            driverObject.updateVariable(pointId, toRecord(point, numeric));
        } catch (IOException e) {
            throw new DriverException("POWERLINK write failed for " + pointId, e);
        }
    }

    private static DataRecord toRecord(EthernetPowerlinkPoint point, double value) {
        return DataRecord.single(VALUE_SCHEMA, Map.of(
                "value", value,
                "kind", point.kindName(),
                "point", point.display()
        ));
    }

    private static double extractNumeric(DataRecord value) {
        if (value == null || value.rowCount() == 0) {
            throw new IllegalArgumentException("POWERLINK write requires a value");
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
        throw new IllegalArgumentException("POWERLINK write requires numeric value/raw");
    }

    private void ensureConnected() throws DriverException {
        if (!isConnected()) {
            throw new DriverException("Not connected");
        }
    }
}
