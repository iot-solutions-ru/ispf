package com.ispf.driver.ethercat;

import com.ispf.core.model.DataRecord;
import com.ispf.core.model.DataSchema;
import com.ispf.core.model.FieldType;
import com.ispf.driver.DeviceDriver;
import com.ispf.driver.DriverException;
import com.ispf.driver.DriverMetadata;
import com.ispf.driver.ethercat.codec.EthercatCodec;
import com.ispf.driver.ethercat.codec.EthercatSession;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * EtherCAT driver — EtherCAT datagrams over TCP (default port {@code 34980}).
 * <p>
 * Carries LRD/LWR datagrams to a serial/raw EtherCAT TCP gateway. This is not an IgH or
 * SOEM master and not a hard real-time fieldbus stack. Point forms: {@code slave:1},
 * {@code slave:1:pdo:0}, {@code 0x6000:01}.
 * <p>
 * Clean-room ISPF code, Apache-2.0 — JDK sockets only.
 */
public class EthercatDeviceDriver implements DeviceDriver {

    private static final DataSchema VALUE_SCHEMA = DataSchema.builder("ethercatValue")
            .field("value", FieldType.DOUBLE)
            .field("kind", FieldType.STRING)
            .field("point", FieldType.STRING)
            .build();

    private static final DriverMetadata METADATA = new DriverMetadata(
            "ethercat",
            "EtherCAT Datagram Driver",
            "1.0.0",
            "EtherCAT datagram over TCP (LRD/LWR logical access);"
                    + " not IgH / SOEM / hard RT master",
            "ISPF",
            Map.of(
                    "host", "127.0.0.1",
                    "port", "34980",
                    "timeoutMs", "3000"
            ),
            null,
            Set.of("read", "write")
    );

    private DriverObject driverObject;
    private String host = "127.0.0.1";
    private int port = 34980;
    private int timeoutMs = 3000;
    private EthercatSession session;
    private final Map<String, EthercatPoint> points = new ConcurrentHashMap<>();

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
            session = new EthercatSession(host, port, timeoutMs);
            driverObject.log(DriverLogLevel.INFO,
                    "EtherCAT datagram TCP connected to " + host + ":" + port
                            + " (not IgH / SOEM / hard RT master)");
        } catch (IOException e) {
            session = null;
            throw new DriverException("EtherCAT connect failed for " + host + ":" + port, e);
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
            EthercatPoint point = EthercatPoint.parse(mapping);
            points.put(entry.getKey(), point);
            try {
                int value = session.readLogicalUint16(point.idx(), point.ado());
                driverObject.updateVariable(entry.getKey(), toRecord(point, value));
            } catch (IOException e) {
                throw new DriverException("EtherCAT read failed for " + mapping, e);
            }
        }
    }

    @Override
    public void writePoint(String pointId, DataRecord value) throws DriverException {
        ensureConnected();
        EthercatPoint point = points.get(pointId);
        if (point == null) {
            throw new DriverException("Unknown point: " + pointId + " (read it first)");
        }
        int word = (int) Math.round(extractNumeric(value)) & 0xFFFF;
        try {
            session.writeLogicalUint16(point.idx(), point.ado(), word);
            driverObject.updateVariable(pointId, toRecord(point, word));
        } catch (IOException e) {
            throw new DriverException("EtherCAT write failed for " + pointId, e);
        }
    }

    private static DataRecord toRecord(EthercatPoint point, int value) {
        return DataRecord.single(VALUE_SCHEMA, Map.of(
                "value", (double) value,
                "kind", point.kindName(),
                "point", point.display()
        ));
    }

    private static double extractNumeric(DataRecord value) {
        if (value == null || value.rowCount() == 0) {
            throw new IllegalArgumentException("EtherCAT write requires a value");
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
        throw new IllegalArgumentException("EtherCAT write requires numeric value/raw");
    }

    private void ensureConnected() throws DriverException {
        if (!isConnected()) {
            throw new DriverException("Not connected");
        }
    }

    public static byte[] buildLrdReferenceFrame() {
        return EthercatCodec.buildLrdReference();
    }
}
