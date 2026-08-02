package com.ispf.driver.mbus;

import com.ispf.core.model.DataRecord;
import com.ispf.core.model.DataSchema;
import com.ispf.core.model.FieldType;
import com.ispf.driver.DeviceDriver;
import com.ispf.driver.DriverException;
import com.ispf.driver.DriverMetadata;
import com.ispf.driver.mbus.codec.MbusTcpClient;

import java.io.IOException;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * M-Bus driver — reads meter registers via the ISPF clean-room TCP codec.
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
            "Reads M-Bus meter registers over TCP using the ISPF codec",
            "ISPF",
            Map.of(
                    "connectionType", "tcp",
                    "host", "127.0.0.1",
                    "port", "10001",
                    "serialPort", "/dev/ttyUSB0",
                    "timeoutMs", "3000"
            )
    );

    private DriverObject driverObject;
    private String connectionType = "tcp";
    private String host = "127.0.0.1";
    private int port = 10001;
    private String serialPort = "/dev/ttyUSB0";
    private int timeoutMs = 3000;
    private MbusTcpClient client;
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
            case "timeoutMs" -> timeoutMs = Integer.parseInt(value.trim());
            default -> { }
        }
    }

    @Override
    public void connect() throws DriverException {
        if ("serial".equals(connectionType)) {
            throw new DriverException("serial not yet implemented in ISPF codec");
        }
        if (!"tcp".equals(connectionType)) {
            throw new DriverException("Unsupported M-Bus connectionType: " + connectionType);
        }
        try {
            client = new MbusTcpClient(host, port, timeoutMs);
            client.connect();
            connected = true;
            driverObject.log(DriverLogLevel.INFO, "M-Bus connected (tcp " + host + ":" + port + ")");
        } catch (IOException e) {
            connected = false;
            throw new DriverException("M-Bus connect failed", e);
        }
    }

    @Override
    public void disconnect() {
        connected = false;
        if (client != null) {
            try {
                client.close();
            } catch (Exception ignored) {
                // best effort
            }
            client = null;
        }
    }

    @Override
    public boolean isConnected() {
        return connected && client != null && client.isConnected();
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
            java.util.List<MbusTcpClient.Record> response = client.read(primaryAddress);
            String value = "";
            String unit = "";
            for (MbusTcpClient.Record record : response) {
                if (record.matches(point.register())) {
                    value = record.value();
                    unit = record.unit();
                    break;
                }
            }
            if (value.isEmpty() && !response.isEmpty()) {
                MbusTcpClient.Record first = response.getFirst();
                value = first.value();
                unit = first.unit();
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
}
