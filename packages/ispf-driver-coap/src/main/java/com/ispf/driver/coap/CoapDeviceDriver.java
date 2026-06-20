package com.ispf.driver.coap;

import com.ispf.core.model.DataRecord;
import com.ispf.core.model.DataSchema;
import com.ispf.core.model.FieldType;
import com.ispf.driver.DeviceDriver;
import com.ispf.driver.DriverException;
import com.ispf.driver.DriverMetadata;
import org.eclipse.californium.core.CoapClient;
import org.eclipse.californium.core.CoapResponse;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * CoAP client driver — GET requests to constrained IoT devices.
 * Point mapping: URI path (e.g. {@code /sensor/temp}) or full coap:// URI.
 */
public class CoapDeviceDriver implements DeviceDriver {

    private static final DataSchema RESPONSE_SCHEMA = DataSchema.builder("coapResponse")
            .field("statusCode", FieldType.INTEGER)
            .field("value", FieldType.STRING)
            .field("contentFormat", FieldType.STRING)
            .build();

    private static final DriverMetadata METADATA = new DriverMetadata(
            "coap",
            "CoAP Client Driver",
            "0.1.0",
            "Polls CoAP resources and maps response payload to ISPF variables",
            "ISPF",
            Map.of(
                    "host", "127.0.0.1",
                    "port", "5683",
                    "timeoutMs", "5000",
                    "pollIntervalMs", "10000"
            )
    );

    private DriverObject driverObject;
    private String host = "127.0.0.1";
    private int port = 5683;
    private long timeoutMs = 5000;
    private CoapClient client;
    private final Map<String, String> points = new ConcurrentHashMap<>();
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
            case "timeoutMs" -> timeoutMs = Long.parseLong(value.trim());
            default -> { }
        }
    }

    @Override
    public void connect() throws DriverException {
        try {
            client = new CoapClient();
            client.setTimeout(timeoutMs);
            connected = true;
            driverObject.log(DriverLogLevel.INFO, "CoAP client ready (" + host + ":" + port + ")");
        } catch (Exception e) {
            connected = false;
            throw new DriverException("CoAP connect failed", e);
        }
    }

    @Override
    public void disconnect() {
        connected = false;
        if (client != null) {
            client.shutdown();
            client = null;
        }
    }

    @Override
    public boolean isConnected() {
        return connected && client != null;
    }

    @Override
    public void readPoints(Map<String, String> pointMappings) throws DriverException {
        if (!isConnected()) {
            throw new DriverException("Not connected");
        }
        points.clear();
        for (Map.Entry<String, String> entry : pointMappings.entrySet()) {
            String path = entry.getValue();
            if (path == null || path.isBlank()) {
                throw new DriverException("CoAP path mapping is blank for " + entry.getKey());
            }
            points.put(entry.getKey(), path);
            driverObject.updateVariable(entry.getKey(), fetch(path));
        }
    }

    @Override
    public void writePoint(String pointId, DataRecord value) throws DriverException {
        throw new DriverException("CoAP driver is read-only in v0.1");
    }

    private DataRecord fetch(String path) throws DriverException {
        String uri = resolveUri(path);
        CoapClient requestClient = new CoapClient(uri);
        try {
            requestClient.setTimeout(timeoutMs);
            CoapResponse response = requestClient.get();
            if (response == null) {
                throw new DriverException("CoAP GET timeout for " + uri);
            }
            String payload = response.getResponseText() == null ? "" : response.getResponseText();
            int contentFormat = response.getOptions().getContentFormat();
            String format = contentFormat >= 0 ? String.valueOf(contentFormat) : "";
            return DataRecord.single(RESPONSE_SCHEMA, Map.of(
                    "statusCode", response.getCode().value,
                    "value", payload,
                    "contentFormat", format
            ));
        } catch (DriverException e) {
            throw e;
        } catch (Exception e) {
            throw new DriverException("CoAP request failed for " + path, e);
        } finally {
            requestClient.shutdown();
        }
    }

    private String resolveUri(String path) {
        if (path.startsWith("coap://") || path.startsWith("coaps://")) {
            return path;
        }
        String normalized = path.startsWith("/") ? path : "/" + path;
        return "coap://" + host + ":" + port + normalized;
    }
}
