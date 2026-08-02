package com.ispf.driver.ipmi;

import com.ispf.core.model.DataRecord;
import com.ispf.core.model.DataSchema;
import com.ispf.core.model.FieldType;
import com.ispf.driver.DeviceDriver;
import com.ispf.driver.DriverException;
import com.ispf.driver.DriverMetadata;
import com.ispf.driver.ipmi.codec.IpmiLanClient;
import com.ispf.driver.ipmi.codec.IpmiSdrRecord;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * IPMI LAN driver with ISPF-owned RMCP command client and SDR parser.
 */
public class IpmiDeviceDriver implements DeviceDriver {

    private static final DataSchema IPMI_SCHEMA = DataSchema.builder("ipmiValue")
            .field("value", FieldType.STRING)
            .field("reachable", FieldType.BOOLEAN)
            .field("powerOn", FieldType.BOOLEAN)
            .field("sensor", FieldType.STRING)
            .build();

    private static final DriverMetadata METADATA = new DriverMetadata(
            "ipmi",
            "IPMI LAN Driver",
            "0.1.0",
            "IPMI LAN sensor and chassis power reads (ISPF clean-room codec)",
            "ISPF",
            Map.of(
                    "host", "127.0.0.1",
                    "port", "623",
                    "username", "",
                    "password", "",
                    "timeoutMs", "5000",
                    "pollIntervalMs", "60000"
            )
    );

    private DriverObject driverObject;
    private String host = "127.0.0.1";
    private int port = 623;
    private String username = "";
    private String password = "";
    private int timeoutMs = 5000;
    private final Map<String, IpmiPoint> points = new ConcurrentHashMap<>();
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
            case "host" -> host = value.trim();
            case "port" -> port = Integer.parseInt(value.trim());
            case "username" -> username = value.trim();
            case "password" -> password = value;
            case "timeoutMs" -> timeoutMs = Integer.parseInt(value.trim());
            default -> { }
        }
    }

    @Override
    public void connect() throws DriverException {
        boolean reachable = RmcpPingClient.ping(host, port, timeoutMs);
        if (!reachable && (username.isBlank() || password.isBlank())) {
            throw new DriverException("IPMI host unreachable at " + host + ":" + port);
        }
        connected = true;
        driverObject.log(DriverLogLevel.INFO, "IPMI ready (" + host + ":" + port + ", reachable=" + reachable + ")");
    }

    @Override
    public void disconnect() {
        connected = false;
    }

    @Override
    public boolean isConnected() {
        return connected;
    }

    @Override
    public void readPoints(Map<String, String> pointMappings) throws DriverException {
        if (!isConnected()) {
            throw new DriverException("Not connected");
        }
        points.clear();
        boolean reachable = RmcpPingClient.ping(host, port, timeoutMs);
        try (IpmiLanClient client = authenticatedClient()) {
            if (!username.isBlank() && !password.isBlank()) {
                client.openSession(username, password);
            }
            for (Map.Entry<String, String> entry : pointMappings.entrySet()) {
                IpmiPoint point = IpmiPoint.parse(entry.getValue());
                points.put(entry.getKey(), point);
                driverObject.updateVariable(entry.getKey(), readPoint(point, client, reachable));
            }
        } catch (Exception e) {
            throw new DriverException("IPMI read failed for " + host + ":" + port, e);
        }
    }

    @Override
    public void writePoint(String pointId, DataRecord value) throws DriverException {
        throw new DriverException("IPMI driver is read-only in v0.1");
    }

    private IpmiLanClient authenticatedClient() throws Exception {
        if (username.isBlank() || password.isBlank()) {
            return null;
        }
        return new IpmiLanClient(host, port, timeoutMs);
    }

    private DataRecord readPoint(IpmiPoint point, IpmiLanClient client, boolean reachable) throws Exception {
        if (client == null) {
            return DataRecord.single(IPMI_SCHEMA, Map.of(
                    "value", reachable ? "reachable" : "unreachable",
                    "reachable", reachable,
                    "powerOn", false,
                    "sensor", ""
            ));
        }
        return switch (point.kind()) {
            case POWER -> readPower(client, reachable);
            case SENSOR -> readSensor(client, point.sensorName(), reachable);
        };
    }

    private DataRecord readPower(IpmiLanClient client, boolean reachable) throws Exception {
        boolean powerOn = client.getChassisPowerOn();
        return DataRecord.single(IPMI_SCHEMA, Map.of(
                "value", powerOn ? "on" : "off",
                "reachable", reachable,
                "powerOn", powerOn,
                "sensor", ""
        ));
    }

    private DataRecord readSensor(IpmiLanClient client, String sensorName, boolean reachable) throws Exception {
        List<IpmiSdrRecord> records = client.readSdrRepository();
        for (IpmiSdrRecord record : records) {
            if (sensorName.equalsIgnoreCase(record.name())) {
                int rawReading = client.getSensorReading(record.sensorNumber());
                return DataRecord.single(IPMI_SCHEMA, Map.of(
                        "value", String.valueOf(record.convertReading(rawReading)),
                        "reachable", reachable,
                        "powerOn", false,
                        "sensor", record.name()
                ));
            }
        }
        return DataRecord.single(IPMI_SCHEMA, Map.of(
                "value", "",
                "reachable", reachable,
                "powerOn", false,
                "sensor", ""
        ));
    }
}
