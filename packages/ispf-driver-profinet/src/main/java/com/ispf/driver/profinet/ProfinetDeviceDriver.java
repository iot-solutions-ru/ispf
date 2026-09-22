package com.ispf.driver.profinet;

import com.ispf.core.model.DataRecord;
import com.ispf.core.model.DataSchema;
import com.ispf.core.model.FieldType;
import com.ispf.driver.DeviceDriver;
import com.ispf.driver.DriverException;
import com.ispf.driver.DriverMaturity;
import com.ispf.driver.DriverMetadata;
import com.ispf.driver.profinet.codec.ProfinetSession;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * PROFINET DCP Identify over a TCP gateway ({@code profinet}).
 * <p>
 * Wire format is the Ethernet DCP Identify Request (FrameID {@code 0xFEFE}) carried on TCP.
 * Optional DCP blocks carry slot/subslot from the point mapping. Not PROFINET RT/IRT.
 * <p>
 * Point forms: {@code slot:1:subslot:1}, {@code device:1:api:0:slot:1}.
 */
public class ProfinetDeviceDriver implements DeviceDriver {

    private static final DataSchema VALUE_SCHEMA = DataSchema.builder("profinetValue")
            .field("value", FieldType.DOUBLE)
            .field("kind", FieldType.STRING)
            .field("point", FieldType.STRING)
            .build();

    private static final DriverMetadata METADATA = new DriverMetadata(
            "profinet",
            "PROFINET DCP Driver",
            "1.0.0",
            "PROFINET DCP Identify over TCP gateway; not PROFINET RT/IRT",
            "ISPF",
            Map.of(
                    "host", "127.0.0.1",
                    "port", "34964",
                    "timeoutMs", "3000"
            ),
            DriverMaturity.BETA,
            Set.of("read", "write")
    );

    private DriverObject driverObject;
    private String host = "127.0.0.1";
    private int port = 34964;
    private int timeoutMs = 3000;
    private ProfinetSession session;
    private final Map<String, ProfinetPoint> points = new ConcurrentHashMap<>();

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
            session = new ProfinetSession(host, port, timeoutMs);
            driverObject.log(DriverLogLevel.INFO,
                    "PROFINET DCP connected to " + host + ":" + port + " (not RT/IRT)");
        } catch (IOException e) {
            session = null;
            throw new DriverException("PROFINET connect failed for " + host + ":" + port, e);
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
            ProfinetPoint point = ProfinetPoint.parse(mapping);
            points.put(entry.getKey(), point);
            try {
                double value = session.readValue(point.slot(), point.subslot());
                driverObject.updateVariable(entry.getKey(), toRecord(point, value));
            } catch (IOException e) {
                throw new DriverException("PROFINET read failed for " + mapping, e);
            }
        }
    }

    @Override
    public void writePoint(String pointId, DataRecord value) throws DriverException {
        ensureConnected();
        ProfinetPoint point = points.get(pointId);
        if (point == null) {
            throw new DriverException("Unknown point: " + pointId + " (read it first)");
        }
        double numeric = extractNumeric(value);
        try {
            session.writeValue(point.slot(), point.subslot(), numeric);
            driverObject.updateVariable(pointId, toRecord(point, numeric));
        } catch (IOException e) {
            throw new DriverException("PROFINET write failed for " + pointId, e);
        }
    }

    private static DataRecord toRecord(ProfinetPoint point, double value) {
        return DataRecord.single(VALUE_SCHEMA, Map.of(
                "value", value,
                "kind", point.kindName(),
                "point", point.display()
        ));
    }

    private static double extractNumeric(DataRecord value) {
        if (value == null || value.rowCount() == 0) {
            throw new IllegalArgumentException("PROFINET write requires a value");
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
        throw new IllegalArgumentException("PROFINET write requires numeric value/raw");
    }

    private void ensureConnected() throws DriverException {
        if (!isConnected()) {
            throw new DriverException("Not connected");
        }
    }
}
