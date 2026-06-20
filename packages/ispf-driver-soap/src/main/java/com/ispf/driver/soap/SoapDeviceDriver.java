package com.ispf.driver.soap;

import com.ispf.core.model.DataRecord;
import com.ispf.core.model.DataSchema;
import com.ispf.core.model.FieldType;
import com.ispf.driver.DeviceDriver;
import com.ispf.driver.DriverException;
import com.ispf.driver.DriverMetadata;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * SOAP driver — POSTs SOAP envelopes and maps response body to ISPF variables.
 */
public class SoapDeviceDriver implements DeviceDriver {

    private static final DataSchema RESPONSE_SCHEMA = DataSchema.builder("soapResponse")
            .field("value", FieldType.STRING)
            .field("statusCode", FieldType.INTEGER)
            .build();

    private static final DriverMetadata METADATA = new DriverMetadata(
            "soap",
            "SOAP Client Driver",
            "0.1.0",
            "POSTs SOAP envelope XML to an endpoint and maps response body to ISPF variables",
            "ISPF",
            Map.of(
                    "endpointUrl", "http://127.0.0.1:8080/soap",
                    "soapAction", "",
                    "timeoutMs", "5000",
                    "pollIntervalMs", "30000"
            )
    );

    private DriverObject driverObject;
    private String endpointUrl = "http://127.0.0.1:8080/soap";
    private String soapAction = "";
    private long timeoutMs = 5000;
    private HttpClient client;
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
        if (value == null) {
            return;
        }
        switch (key) {
            case "endpointUrl" -> endpointUrl = value.trim();
            case "soapAction" -> soapAction = value.trim();
            case "timeoutMs" -> timeoutMs = Long.parseLong(value.trim());
            default -> { }
        }
    }

    @Override
    public void connect() throws DriverException {
        client = HttpClient.newBuilder()
                .connectTimeout(Duration.ofMillis(timeoutMs))
                .build();
        connected = true;
        driverObject.log(DriverLogLevel.INFO, "SOAP client ready (endpoint=" + endpointUrl + ")");
    }

    @Override
    public void disconnect() {
        connected = false;
        client = null;
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
            String envelope = entry.getValue();
            if (envelope == null || envelope.isBlank()) {
                throw new DriverException("SOAP envelope mapping is blank for " + entry.getKey());
            }
            points.put(entry.getKey(), envelope);
            driverObject.updateVariable(entry.getKey(), post(envelope));
        }
    }

    @Override
    public void writePoint(String pointId, DataRecord value) throws DriverException {
        throw new DriverException("SOAP driver is read-only in v0.1");
    }

    private DataRecord post(String envelope) throws DriverException {
        try {
            HttpRequest.Builder builder = HttpRequest.newBuilder()
                    .uri(URI.create(endpointUrl))
                    .timeout(Duration.ofMillis(timeoutMs))
                    .header("Content-Type", "text/xml; charset=utf-8")
                    .POST(HttpRequest.BodyPublishers.ofString(envelope));
            if (!soapAction.isEmpty()) {
                builder.header("SOAPAction", soapAction);
            }
            HttpResponse<String> response = client.send(builder.build(), HttpResponse.BodyHandlers.ofString());
            String body = response.body() == null ? "" : response.body();
            return DataRecord.single(RESPONSE_SCHEMA, Map.of(
                    "value", body,
                    "statusCode", response.statusCode()
            ));
        } catch (Exception e) {
            throw new DriverException("SOAP request failed", e);
        }
    }
}
