package com.ispf.driver.dnp3;

import com.ispf.core.model.DataRecord;
import com.ispf.core.model.DataSchema;
import com.ispf.core.model.FieldType;
import com.ispf.driver.DeviceDriver;
import com.ispf.driver.DriverConfigurationException;
import com.ispf.driver.DriverException;
import com.ispf.driver.DriverMetadata;
import com.ispf.driver.DriverPermanentException;
import com.ispf.driver.DriverTransientException;

import java.util.HashMap;
import java.util.Map;

/**
 * DNP3 TCP master driver with ISPF-owned Class 0/1/2/3 integrity poll codec.
 * <p>
 * Point mapping: {@code index:dataType} e.g. {@code 0:ANALOG_INPUT}.
 */
public class Dnp3DeviceDriver implements DeviceDriver {

    private static final DriverMetadata METADATA = new DriverMetadata(
            "dnp3",
            "DNP3 Driver",
            "0.2.0",
            "DNP3 TCP master with Class 0/1/2/3 poll and direct operate (ISPF clean-room codec)",
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
    /** Replaced as a whole after each poll so a later write never sees an empty map. */
    private volatile Map<String, Dnp3Point> points = Map.of();

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
            throw new DriverTransientException("Not connected");
        }
        Map<String, Dnp3Point> next = new HashMap<>();
        for (Map.Entry<String, String> entry : pointMappings.entrySet()) {
            next.put(entry.getKey(), Dnp3Point.parse(entry.getValue()));
        }
        session.pollAllClasses();
        Dnp3ReadCache cache = session.cache();
        for (Map.Entry<String, Dnp3Point> entry : next.entrySet()) {
            try {
                driverObject.updateVariable(entry.getKey(), readPoint(entry.getValue(), cache));
            } catch (DriverException ex) {
                driverObject.updateVariable(entry.getKey(), unavailableRecord(entry.getValue()));
                driverObject.log(DriverLogLevel.DEBUG, "DNP3 read skipped for " + entry.getKey() + ": " + ex.getMessage());
            }
        }
        points = Map.copyOf(next);
    }

    @Override
    public void writePoint(String pointId, DataRecord value) throws DriverException {
        if (!isConnected()) {
            throw new DriverTransientException("Not connected");
        }
        Dnp3Point point = points.get(pointId);
        if (point == null) {
            throw new DriverConfigurationException("Unknown point: " + pointId);
        }
        switch (point.dataType()) {
            case BINARY_OUTPUT -> session.directOperate(point, extractBoolean(value));
            case ANALOG_OUTPUT -> session.directOperate(point, extractDouble(value));
            default -> throw new DriverPermanentException("DNP3 point type cannot be written: " + point.dataType());
        }
        driverObject.updateVariable(pointId, writeResult(point, value));
    }

    private static DataRecord writeResult(Dnp3Point point, DataRecord value) throws DriverException {
        return switch (point.dataType()) {
            case BINARY_OUTPUT -> DataRecord.single(BOOL_VALUE_SCHEMA, Map.of(
                    "value", extractBoolean(value),
                    "status", "0x01"
            ));
            case ANALOG_OUTPUT -> DataRecord.single(VALUE_SCHEMA, Map.of(
                    "value", extractDouble(value),
                    "status", "0x01"
            ));
            default -> throw new DriverPermanentException("DNP3 point type cannot be written: " + point.dataType());
        };
    }

    private static boolean extractBoolean(DataRecord value) throws DriverException {
        Object raw = value.firstRow().get("value");
        if (raw == null) {
            raw = value.firstRow().get("raw");
        }
        if (raw instanceof Boolean bool) {
            return bool;
        }
        if (raw instanceof Number number) {
            double numeric = number.doubleValue();
            if (numeric == 1.0) {
                return true;
            }
            if (numeric == 0.0) {
                return false;
            }
            throw new DriverPermanentException("DNP3 boolean write requires 0 or 1: " + raw);
        }
        if (raw == null) {
            throw new DriverPermanentException("DNP3 write requires boolean value field");
        }
        String text = String.valueOf(raw).trim().toLowerCase(java.util.Locale.ROOT);
        return switch (text) {
            case "true", "1", "on" -> true;
            case "false", "0", "off" -> false;
            default -> throw new DriverPermanentException("DNP3 boolean write requires true or false: " + raw);
        };
    }

    private static double extractDouble(DataRecord value) throws DriverException {
        Object raw = value.firstRow().get("value");
        if (raw == null) {
            raw = value.firstRow().get("raw");
        }
        if (raw instanceof Number number) {
            return finiteFloat32(number.doubleValue(), raw);
        }
        if (raw == null) {
            throw new DriverPermanentException("DNP3 write requires numeric value field");
        }
        try {
            return finiteFloat32(Double.parseDouble(String.valueOf(raw)), raw);
        } catch (NumberFormatException ex) {
            throw new DriverPermanentException("DNP3 write requires numeric value: " + raw, ex);
        }
    }

    private static double finiteFloat32(double value, Object raw) throws DriverException {
        if (!Double.isFinite(value) || !Float.isFinite((float) value)) {
            throw new DriverPermanentException("DNP3 analog write requires a finite float32 value: " + raw);
        }
        return value;
    }

    private DataRecord readPoint(Dnp3Point point, Dnp3ReadCache cache) throws DriverException {
        Object raw = cache.valueFor(point);
        if (raw == null) {
            throw new DriverConfigurationException("No value for index " + point.index() + " type " + point.dataType());
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
