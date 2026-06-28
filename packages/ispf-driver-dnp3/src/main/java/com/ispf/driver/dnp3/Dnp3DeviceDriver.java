package com.ispf.driver.dnp3;

import com.ispf.core.model.DataRecord;
import com.ispf.core.model.DataSchema;
import com.ispf.core.model.FieldType;
import com.ispf.driver.DeviceDriver;
import com.ispf.driver.DriverException;
import com.ispf.driver.DriverMetadata;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * DNP3 TCP master driver — Class 0/1/2/3 integrity poll via {@code io.stepfunc:dnp3}.
 * <p>
 * Point mapping: {@code index:dataType} e.g. {@code 0:ANALOG_INPUT}.
 */
public class Dnp3DeviceDriver implements DeviceDriver {

    private static final DriverMetadata METADATA = new DriverMetadata(
            "dnp3",
            "DNP3 Driver",
            "0.2.0",
            "DNP3 TCP master with Class 0/1/2/3 poll (io.stepfunc:dnp3)",
            "ISPF",
            Map.of(
                    "host", "127.0.0.1",
                    "port", "20000",
                    "localAddress", "1",
                    "outstationAddress", "1024",
                    "timeoutMs", "5000",
                    "pollIntervalMs", "1000"
            )
    );

    private static final DataSchema VALUE_SCHEMA = DataSchema.builder("dnp3Value")
            .field("value", FieldType.DOUBLE)
            .field("status", FieldType.STRING)
            .build();

    private static final DataSchema BOOL_VALUE_SCHEMA = DataSchema.builder("dnp3BoolValue")
            .field("value", FieldType.BOOLEAN)
            .field("status", FieldType.STRING)
            .build();

    private static final DataSchema COUNTER_VALUE_SCHEMA = DataSchema.builder("dnp3CounterValue")
            .field("value", FieldType.LONG)
            .field("status", FieldType.STRING)
            .build();

    private DriverObject driverObject;
    private Dnp3MasterSession session;
    private String host = "127.0.0.1";
    private int port = 20000;
    private int masterAddress = 1;
    private int outstationAddress = 1024;
    private int timeoutMs = 5000;
    private final Map<String, Dnp3Point> points = new ConcurrentHashMap<>();

    @Override
    public DriverMetadata metadata() {
        return METADATA;
    }

    @Override
    public void initialize(DriverObject driverObject) {
        this.driverObject = driverObject;
        readConfig("host", value -> host = value);
        readConfig("port", value -> port = Integer.parseInt(value));
        readConfig("localAddress", value -> masterAddress = parseAddress(value, 1));
        readConfig("outstationAddress", value -> outstationAddress = parseAddress(value, 1024));
        readConfig("timeoutMs", value -> timeoutMs = Integer.parseInt(value));
    }

    @Override
    public void connect() throws DriverException {
        disconnect();
        session = new Dnp3MasterSession(host, port, masterAddress, outstationAddress, timeoutMs);
        session.connect();
        driverObject.log(
                DriverLogLevel.INFO,
                "DNP3 master connected to " + host + ":" + port
                        + " (master=" + masterAddress + ", outstation=" + outstationAddress + ")"
        );
    }

    @Override
    public void disconnect() {
        if (session != null) {
            session.close();
            session = null;
        }
    }

    @Override
    public boolean isConnected() {
        return session != null && session.isConnected();
    }

    @Override
    public void readPoints(Map<String, String> pointMappings) throws DriverException {
        if (!isConnected()) {
            throw new DriverException("Not connected");
        }
        points.clear();
        session.pollAllClasses();
        Dnp3ReadCache cache = session.cache();
        for (Map.Entry<String, String> entry : pointMappings.entrySet()) {
            Dnp3Point point = Dnp3Point.parse(entry.getValue());
            points.put(entry.getKey(), point);
            try {
                driverObject.updateVariable(entry.getKey(), readPoint(point, cache));
            } catch (DriverException ex) {
                driverObject.updateVariable(entry.getKey(), unavailableRecord(point));
                driverObject.log(DriverLogLevel.DEBUG, "DNP3 read skipped for " + entry.getKey() + ": " + ex.getMessage());
            }
        }
    }

    @Override
    public void writePoint(String pointId, DataRecord value) throws DriverException {
        throw new DriverException("DNP3 write not implemented");
    }

    private DataRecord readPoint(Dnp3Point point, Dnp3ReadCache cache) throws DriverException {
        Object raw = cache.valueFor(point);
        if (raw == null) {
            throw new DriverException("No value for index " + point.index() + " type " + point.dataType());
        }
        String status = cache.qualityFor(point);
        return switch (point.dataType()) {
            case BINARY_INPUT, BINARY_OUTPUT -> DataRecord.single(BOOL_VALUE_SCHEMA, Map.of(
                    "value", (Boolean) raw,
                    "status", status
            ));
            case COUNTER -> DataRecord.single(COUNTER_VALUE_SCHEMA, Map.of(
                    "value", (Long) raw,
                    "status", status
            ));
            case ANALOG_INPUT, ANALOG_OUTPUT -> DataRecord.single(VALUE_SCHEMA, Map.of(
                    "value", ((Number) raw).doubleValue(),
                    "status", status
            ));
        };
    }

    private static DataRecord unavailableRecord(Dnp3Point point) {
        return switch (point.dataType()) {
            case BINARY_INPUT, BINARY_OUTPUT -> DataRecord.single(BOOL_VALUE_SCHEMA, Map.of(
                    "value", false,
                    "status", "UNAVAILABLE"
            ));
            case COUNTER -> DataRecord.single(COUNTER_VALUE_SCHEMA, Map.of(
                    "value", 0L,
                    "status", "UNAVAILABLE"
            ));
            case ANALOG_INPUT, ANALOG_OUTPUT -> DataRecord.single(VALUE_SCHEMA, Map.of(
                    "value", 0.0,
                    "status", "UNAVAILABLE"
            ));
        };
    }

    private static int parseAddress(String value, int defaultValue) {
        int parsed = Integer.parseInt(value.trim());
        return parsed > 0 ? parsed : defaultValue;
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
