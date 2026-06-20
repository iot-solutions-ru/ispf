package com.ispf.driver.jmx;

import com.ispf.core.model.DataRecord;
import com.ispf.core.model.DataSchema;
import com.ispf.core.model.FieldType;
import com.ispf.driver.DeviceDriver;
import com.ispf.driver.DriverException;
import com.ispf.driver.DriverMetadata;

import javax.management.MBeanServerConnection;
import javax.management.ObjectName;
import javax.management.openmbean.CompositeData;
import javax.management.remote.JMXConnector;
import javax.management.remote.JMXConnectorFactory;
import javax.management.remote.JMXServiceURL;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Remote JMX driver — reads MBean attributes via standard javax.management.
 */
public class JmxDeviceDriver implements DeviceDriver {

    private static final DataSchema ATTRIBUTE_SCHEMA = DataSchema.builder("jmxAttribute")
            .field("value", FieldType.STRING)
            .build();

    private static final DriverMetadata METADATA = new DriverMetadata(
            "jmx",
            "JMX Remote Driver",
            "0.1.0",
            "Reads remote JMX MBean attributes (objectName::attributeName)",
            "ISPF",
            Map.of(
                    "host", "127.0.0.1",
                    "port", "9010",
                    "serviceUrl", "",
                    "timeoutMs", "5000",
                    "pollIntervalMs", "10000"
            )
    );

    private DriverObject driverObject;
    private String host = "127.0.0.1";
    private int port = 9010;
    private String serviceUrl = "";
    private int timeoutMs = 5000;
    private JMXConnector connector;
    private MBeanServerConnection connection;
    private final Map<String, JmxPoint> points = new ConcurrentHashMap<>();
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
            case "serviceUrl" -> serviceUrl = value.trim();
            case "timeoutMs" -> timeoutMs = Integer.parseInt(value.trim());
            default -> { }
        }
    }

    @Override
    public void connect() throws DriverException {
        try {
            String url = serviceUrl.isEmpty()
                    ? "service:jmx:rmi:///jndi/rmi://" + host + ":" + port + "/jmxrmi"
                    : serviceUrl;
            connector = JMXConnectorFactory.connect(new JMXServiceURL(url));
            connection = connector.getMBeanServerConnection();
            connected = true;
            driverObject.log(DriverLogLevel.INFO, "JMX connected to " + url);
        } catch (Exception e) {
            throw new DriverException("JMX connect failed", e);
        }
    }

    @Override
    public void disconnect() {
        connected = false;
        if (connector != null) {
            try {
                connector.close();
            } catch (Exception ignored) {
                // best effort
            }
            connector = null;
        }
        connection = null;
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
            JmxPoint point = JmxPoint.parse(entry.getValue());
            points.put(entry.getKey(), point);
            driverObject.updateVariable(entry.getKey(), readAttribute(point));
        }
    }

    @Override
    public void writePoint(String pointId, DataRecord value) throws DriverException {
        throw new DriverException("JMX driver is read-only in v0.1");
    }

    private DataRecord readAttribute(JmxPoint point) throws DriverException {
        try {
            ObjectName name = ObjectName.getInstance(point.objectName());
            Object raw = connection.getAttribute(name, point.attribute());
            String value = formatValue(raw, point.compositeKey());
            return DataRecord.single(ATTRIBUTE_SCHEMA, Map.of("value", value));
        } catch (Exception e) {
            throw new DriverException("JMX read failed for " + point.objectName(), e);
        }
    }

    private static String formatValue(Object raw, String compositeKey) {
        if (raw == null) {
            return "";
        }
        if (compositeKey != null && raw instanceof CompositeData composite) {
            Object nested = composite.get(compositeKey);
            return nested == null ? "" : String.valueOf(nested);
        }
        return String.valueOf(raw);
    }
}
