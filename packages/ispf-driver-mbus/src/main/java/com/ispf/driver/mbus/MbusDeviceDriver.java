package com.ispf.driver.mbus;

import com.ispf.core.model.DataRecord;
import com.ispf.core.model.DataSchema;
import com.ispf.core.model.FieldType;
import com.ispf.driver.DeviceDriver;
import com.ispf.driver.DriverException;
import com.ispf.driver.DriverMetadata;
import org.openmuc.jmbus.MBusConnection;
import org.openmuc.jmbus.VariableDataStructure;

import java.io.IOException;
import java.util.Arrays;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * M-Bus driver — reads meter registers via jMBus (serial or TCP).
 */
public class MbusDeviceDriver implements DeviceDriver {

    private static final DataSchema REGISTER_SCHEMA = DataSchema.builder("mbusRegister")
            .field("value", FieldType.STRING)
            .field("register", FieldType.STRING)
            .field("unit", FieldType.STRING)
            .build();

    private static final DriverMetadata METADATA = new DriverMetadata(
            "mbus",
            "M-Bus Driver",
            "0.1.0",
            "Reads M-Bus meter registers over serial or TCP using jMBus",
            "ISPF",
            Map.of(
                    "connectionType", "tcp",
                    "host", "127.0.0.1",
                    "port", "10001",
                    "serialPort", "/dev/ttyUSB0"
            )
    );

    private DriverObject driverObject;
    private String connectionType = "tcp";
    private String host = "127.0.0.1";
    private int port = 10001;
    private String serialPort = "/dev/ttyUSB0";
    private MBusConnection connection;
    private final Map<String, MbusPoint> points = new ConcurrentHashMap<>();
    private volatile boolean connected;

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
            case "connectionType" -> connectionType = value.trim().toLowerCase(Locale.ROOT);
            case "host" -> host = value.trim();
            case "port" -> port = Integer.parseInt(value.trim());
            case "serialPort" -> serialPort = value.trim();
            default -> { }
        }
    }

    @Override
    public void connect() throws DriverException {
        try {
            if ("serial".equals(connectionType)) {
                connection = MBusConnection.newSerialBuilder(serialPort).setBaudrate(2400).build();
            } else {
                connection = MBusConnection.newTcpBuilder(host, port).build();
            }
            connected = true;
            driverObject.log(DriverLogLevel.INFO, "M-Bus connected (" + connectionType + ")");
        } catch (IOException e) {
            throw new DriverException("M-Bus connect failed", e);
        }
    }

    @Override
    public void disconnect() {
        connected = false;
        if (connection != null) {
            try {
                connection.close();
            } catch (Exception ignored) {
                // best effort
            }
            connection = null;
        }
    }

    @Override
    public boolean isConnected() {
        return connected && connection != null;
    }

    @Override
    public void readPoints(Map<String, String> pointMappings) throws DriverException {
        if (!isConnected()) {
            throw new DriverException("Not connected");
        }
        points.clear();
        for (Map.Entry<String, String> entry : pointMappings.entrySet()) {
            MbusPoint point = MbusPoint.parse(entry.getValue());
            points.put(entry.getKey(), point);
            driverObject.updateVariable(entry.getKey(), readRegister(point));
        }
    }

    @Override
    public void writePoint(String pointId, DataRecord value) throws DriverException {
        throw new DriverException("M-Bus driver is read-only in v0.1");
    }

    private DataRecord readRegister(MbusPoint point) throws DriverException {
        try {
            int primaryAddress = point.primaryAddress();
            if (point.secondaryAddress() > 0) {
                primaryAddress = 0xFD;
                driverObject.log(DriverLogLevel.DEBUG,
                        "Secondary address " + point.secondaryAddress() + " configured; using primary 0xFD read");
            }
            VariableDataStructure response = connection.read(primaryAddress);
            String value = "";
            String unit = "";
            for (org.openmuc.jmbus.DataRecord record : response.getDataRecords()) {
                if (matchesRegister(record, point.register())) {
                    Object dataValue = record.getDataValue();
                    value = dataValue == null ? "" : dataValue.toString();
                    unit = record.getUnit() == null ? "" : String.valueOf(record.getUnit());
                    break;
                }
            }
            if (value.isEmpty() && !response.getDataRecords().isEmpty()) {
                org.openmuc.jmbus.DataRecord first = response.getDataRecords().get(0);
                Object dataValue = first.getDataValue();
                value = dataValue == null ? "" : dataValue.toString();
                unit = first.getUnit() == null ? "" : String.valueOf(first.getUnit());
            }
            return DataRecord.single(REGISTER_SCHEMA, Map.of(
                    "value", value,
                    "register", point.register(),
                    "unit", unit
            ));
        } catch (Exception e) {
            throw new DriverException("M-Bus read failed for " + point, e);
        }
    }

    private static boolean matchesRegister(org.openmuc.jmbus.DataRecord record, String register) {
        String dibVib = Arrays.toString(record.getDib()) + "-" + Arrays.toString(record.getVib());
        String description = record.getUserDefinedDescription();
        if (description == null || description.isBlank()) {
            description = record.getDescription() == null ? "" : record.getDescription().toString();
        }
        return register.equalsIgnoreCase(dibVib)
                || register.equalsIgnoreCase(description)
                || register.equalsIgnoreCase(String.valueOf(record.getFunctionField()));
    }
}
