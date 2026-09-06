package com.ispf.driver.lwm2m;

import com.ispf.core.model.DataRecord;
import com.ispf.core.model.DataSchema;
import com.ispf.core.model.FieldType;
import com.ispf.driver.DeviceDriver;
import com.ispf.driver.DriverException;
import com.ispf.driver.DriverMetadata;

import java.io.IOException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * OMA LwM2M client subset — resource read via clean-room CoAP GET (RFC 7252 CON/ACK).
 * <p>
 * Point mapping is an LwM2M path such as {@code /1/0/0} (Object/Instance/Resource) or
 * {@code /rd} (registration interface probe). This is <strong>not</strong> a full LwM2M
 * bootstrap/registration/observe stack: only synchronous CoAP GET of Uri-Path resources
 * over UDP. Public OMA LwM2M / IETF CoAP specs only.
 * <p>
 * Clean-room ISPF code, Apache-2.0 — no Eclipse Californium (avoids EPL/GPL dependency).
 */
public class Lwm2mDeviceDriver implements DeviceDriver {

    private static final DataSchema VALUE_SCHEMA = DataSchema.builder("lwm2mValue")
            .field("value", FieldType.STRING)
            .field("path", FieldType.STRING)
            .field("code", FieldType.INTEGER)
            .build();

    private static final DriverMetadata METADATA = new DriverMetadata(
            "lwm2m",
            "LwM2M Driver",
            "0.1.0",
            "OMA LwM2M resource-read subset via clean-room CoAP GET (/object/instance/resource)",
            "ISPF",
            Map.of(
                    "host", "127.0.0.1",
                    "port", "5683",
                    "timeoutMs", "3000",
                    "registerPath", "/rd",
                    "probeRegisterOnConnect", "true",
                    "pollIntervalMs", "10000"
            )
    );

    private DriverObject driverObject;
    private String host = "127.0.0.1";
    private int port = 5683;
    private int timeoutMs = 3000;
    private String registerPath = "/rd";
    private boolean probeRegisterOnConnect = true;
    private final Map<String, String> points = new ConcurrentHashMap<>();
    private volatile boolean connected;
    private String registerProbe = "";

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
            case "registerPath" -> registerPath = CoapGetClient.normalizePath(value.trim());
            case "probeRegisterOnConnect" -> probeRegisterOnConnect = Boolean.parseBoolean(value.trim());
            default -> { }
        }
    }

    @Override
    public void connect() throws DriverException {
        if (probeRegisterOnConnect) {
            try {
                CoapGetClient.Response response = CoapGetClient.get(host, port, registerPath, timeoutMs);
                if (response.code() != CoapGetClient.CODE_CONTENT) {
                    throw new DriverException("LwM2M /rd probe expected 2.05 Content, got code=" + response.code());
                }
                registerProbe = response.payload();
            } catch (DriverException e) {
                throw e;
            } catch (IOException e) {
                throw new DriverException("LwM2M connect probe failed for " + host + ":" + port + " " + registerPath, e);
            }
        }
        connected = true;
        driverObject.log(DriverLogLevel.INFO,
                "LwM2M CoAP GET client ready for " + host + ":" + port
                        + " (subset: resource read only; rdProbe=" + registerProbe + ")");
    }

    @Override
    public void disconnect() {
        connected = false;
        points.clear();
        registerProbe = "";
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
        for (Map.Entry<String, String> entry : pointMappings.entrySet()) {
            String pointId = entry.getKey();
            String mapping = entry.getValue() == null || entry.getValue().isBlank() ? pointId : entry.getValue();
            points.put(pointId, mapping);
            String path = CoapGetClient.normalizePath(mapping);
            try {
                CoapGetClient.Response response = CoapGetClient.get(host, port, path, timeoutMs);
                if (response.code() != CoapGetClient.CODE_CONTENT) {
                    throw new DriverException("LwM2M GET " + path + " returned code=" + response.code());
                }
                driverObject.updateVariable(pointId, DataRecord.single(VALUE_SCHEMA, Map.of(
                        "value", response.payload(),
                        "path", path,
                        "code", response.code()
                )));
            } catch (DriverException e) {
                throw e;
            } catch (IOException e) {
                throw new DriverException("LwM2M GET failed for " + path, e);
            }
        }
    }

    @Override
    public void writePoint(String pointId, DataRecord value) throws DriverException {
        throw new DriverException("LwM2M driver is read-only in v0.1 (CoAP GET subset only)");
    }
}
