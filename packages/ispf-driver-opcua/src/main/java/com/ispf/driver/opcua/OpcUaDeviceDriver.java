package com.ispf.driver.opcua;

import com.ispf.core.model.DataRecord;
import com.ispf.core.model.DataSchema;
import com.ispf.core.model.FieldType;
import com.ispf.driver.DeviceDriver;
import com.ispf.driver.DriverException;
import com.ispf.driver.DriverMetadata;
import org.eclipse.milo.opcua.sdk.client.OpcUaClient;
import org.eclipse.milo.opcua.stack.core.security.SecurityPolicy;
import org.eclipse.milo.opcua.stack.core.types.builtin.DataValue;
import org.eclipse.milo.opcua.stack.core.types.builtin.LocalizedText;
import org.eclipse.milo.opcua.stack.core.types.enumerated.TimestampsToReturn;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

/**
 * OPC UA client driver — reads node Value attributes via Eclipse Milo.
 * <p>
 * Point mapping: {@code nodeId} e.g. {@code ns=2;s=Temperature} or {@code i=2258}.
 */
public class OpcUaDeviceDriver implements DeviceDriver {

    private static final DriverMetadata METADATA = new DriverMetadata(
            "opcua",
            "OPC UA Driver",
            "0.1.0",
            "Polls OPC UA servers (SecurityPolicy None) and maps node values to ISPF variables",
            "ISPF",
            Map.of(
                    "endpointUrl", "opc.tcp://localhost:4840",
                    "timeoutMs", "5000",
                    "pollIntervalMs", "1000"
            )
    );

    private static final DataSchema VALUE_SCHEMA = DataSchema.builder("opcUaValue")
            .field("value", FieldType.STRING)
            .field("quality", FieldType.STRING)
            .build();

    private DriverObject driverObject;
    private OpcUaClient client;
    private String endpointUrl = "opc.tcp://localhost:4840";
    private int timeoutMs = 5000;
    private final Map<String, OpcUaPoint> points = new ConcurrentHashMap<>();
    private volatile boolean connected;

    @Override
    public DriverMetadata metadata() {
        return METADATA;
    }

    @Override
    public void initialize(DriverObject driverObject) {
        this.driverObject = driverObject;
        readConfig("endpointUrl", value -> endpointUrl = value);
        readConfig("timeoutMs", value -> timeoutMs = Integer.parseInt(value));
    }

    @Override
    public void connect() throws DriverException {
        try {
            client = OpcUaClient.create(
                    endpointUrl,
                    endpoints -> endpoints.stream()
                            .filter(endpoint -> SecurityPolicy.None.getUri().equals(endpoint.getSecurityPolicyUri()))
                            .findFirst(),
                    configBuilder -> configBuilder
                            .setApplicationName(LocalizedText.english("ISPF OPC UA Driver"))
                            .setApplicationUri("urn:ispf:driver:opcua")
                            .build()
            );
            client.connect().get(timeoutMs, TimeUnit.MILLISECONDS);
            connected = true;
            driverObject.log(DriverLogLevel.INFO, "Connected to OPC UA " + endpointUrl);
        } catch (Exception e) {
            connected = false;
            client = null;
            throw new DriverException("OPC UA connect failed", e);
        }
    }

    @Override
    public void disconnect() {
        connected = false;
        if (client != null) {
            try {
                client.disconnect().get(timeoutMs, TimeUnit.MILLISECONDS);
            } catch (Exception ignored) {
                // best effort
            }
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
            OpcUaPoint point = OpcUaPoint.parse(entry.getValue());
            points.put(entry.getKey(), point);
            driverObject.updateVariable(entry.getKey(), readPoint(point));
        }
    }

    @Override
    public void writePoint(String pointId, DataRecord value) throws DriverException {
        throw new DriverException("OPC UA write not implemented");
    }

    private DataRecord readPoint(OpcUaPoint point) throws DriverException {
        try {
            DataValue dataValue = client.readValue(
                    0.0,
                    TimestampsToReturn.Neither,
                    point.nodeId()
            ).get(timeoutMs, TimeUnit.MILLISECONDS);

            Object rawValue = dataValue.getValue() != null ? dataValue.getValue().getValue() : null;
            String quality = dataValue.getStatusCode() != null ? dataValue.getStatusCode().toString() : "UNKNOWN";
            return DataRecord.single(VALUE_SCHEMA, Map.of(
                    "value", rawValue != null ? rawValue.toString() : "",
                    "quality", quality
            ));
        } catch (Exception e) {
            throw new DriverException("OPC UA read failed at " + point.nodeId(), e);
        }
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
