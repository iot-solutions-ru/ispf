package com.ispf.driver.virtual;

import com.ispf.core.model.DataRecord;
import com.ispf.core.model.DataSchema;
import com.ispf.core.model.FieldType;
import com.ispf.driver.DeviceDriver;
import com.ispf.driver.DriverException;
import com.ispf.driver.DriverMetadata;

import java.time.Instant;
import java.util.Map;

/**
 * Simulated device driver for demos and integration tests.
 * Supports temperature, tank level, and connectivity simulation via point mappings.
 */
public class VirtualDeviceDriver implements DeviceDriver {

    private static final DriverMetadata METADATA = new DriverMetadata(
            "virtual",
            "Virtual Simulator Driver",
            "0.1.0",
            "Generates synthetic telemetry for stand/demo environments",
            "ISPF",
            Map.of(
                    "baseTemperature", "22.0",
                    "amplitude", "15.0",
                    "periodSec", "60",
                    "baseLevelM3", "1200",
                    "levelAmplitudeM3", "8",
                    "tempAmplitude", "2",
                    "pollIntervalMs", "2000"
            )
    );

    private static final DataSchema TEMPERATURE_SCHEMA = DataSchema.builder("temperature")
            .field("value", FieldType.DOUBLE)
            .field("unit", FieldType.STRING)
            .build();

    private static final DataSchema DOUBLE_VALUE_SCHEMA = DataSchema.builder("doubleValue")
            .field("value", FieldType.DOUBLE)
            .build();

    private static final DataSchema STATUS_SCHEMA = DataSchema.builder("deviceStatus")
            .field("online", FieldType.BOOLEAN)
            .field("lastSeen", FieldType.STRING)
            .build();

    private DriverObject driverObject;
    private double baseTemperature = 22.0;
    private double amplitude = 15.0;
    private double periodSec = 60.0;
    private double baseLevelM3 = 1200.0;
    private double levelAmplitudeM3 = 8.0;
    private double tempAmplitude = 2.0;
    private final long startedAt = System.currentTimeMillis();
    private volatile boolean connected;

    @Override
    public DriverMetadata metadata() {
        return METADATA;
    }

    @Override
    public void initialize(DriverObject driverObject) {
        this.driverObject = driverObject;
        driverObject.configuration().forEach(this::applyConfig);
        readConfig("baseTemperature", value -> baseTemperature = Double.parseDouble(value));
        readConfig("amplitude", value -> amplitude = Double.parseDouble(value));
        readConfig("periodSec", value -> periodSec = Double.parseDouble(value));
        readConfig("baseLevelM3", value -> baseLevelM3 = Double.parseDouble(value));
        readConfig("levelAmplitudeM3", value -> levelAmplitudeM3 = Double.parseDouble(value));
        readConfig("tempAmplitude", value -> tempAmplitude = Double.parseDouble(value));
    }

    private void applyConfig(String key, String value) {
        switch (key) {
            case "baseTemperature" -> baseTemperature = Double.parseDouble(value);
            case "amplitude" -> amplitude = Double.parseDouble(value);
            case "periodSec" -> periodSec = Double.parseDouble(value);
            case "baseLevelM3" -> baseLevelM3 = Double.parseDouble(value);
            case "levelAmplitudeM3" -> levelAmplitudeM3 = Double.parseDouble(value);
            case "tempAmplitude" -> tempAmplitude = Double.parseDouble(value);
            default -> { }
        }
    }

    @Override
    public void connect() {
        connected = true;
        driverObject.log(DriverLogLevel.INFO, "Virtual driver connected");
    }

    @Override
    public void disconnect() {
        connected = false;
        driverObject.log(DriverLogLevel.INFO, "Virtual driver disconnected");
    }

    @Override
    public boolean isConnected() {
        return connected;
    }

    @Override
    public void readPoints(Map<String, String> pointMappings) throws DriverException {
        if (!connected) {
            throw new DriverException("Not connected");
        }
        if (pointMappings == null || pointMappings.isEmpty()) {
            publishLegacyTemperature();
            return;
        }

        boolean handled = false;
        for (Map.Entry<String, String> entry : pointMappings.entrySet()) {
            String variableName = entry.getKey();
            String mode = entry.getValue();
            switch (mode) {
                case "sim", "sim-temperature" -> {
                    publishTemperature(variableName, tempAmplitude);
                    handled = true;
                }
                case "sim-tank-level" -> {
                    publishTankLevel(variableName);
                    handled = true;
                }
                case "sim-status" -> {
                    publishStatus(variableName);
                    handled = true;
                }
                default -> driverObject.log(DriverLogLevel.WARNING, "Unknown virtual sim mode: " + mode);
            }
        }
        if (!handled) {
            publishLegacyTemperature();
        }
    }

    @Override
    public void writePoint(String pointId, DataRecord value) throws DriverException {
        throw new DriverException("Virtual driver is read-only");
    }

    private void publishLegacyTemperature() {
        publishTemperature("temperature", amplitude);
        publishStatus("status");
    }

    private void publishTemperature(String variableName, double waveAmplitude) {
        double elapsedSec = elapsedSeconds();
        double temperature = baseTemperature + waveAmplitude * Math.sin(2 * Math.PI * elapsedSec / periodSec);
        driverObject.updateVariable(
                variableName,
                DataRecord.single(TEMPERATURE_SCHEMA, Map.of("value", temperature, "unit", "C"))
        );
    }

    private void publishTankLevel(String variableName) {
        double elapsedSec = elapsedSeconds();
        double level = baseLevelM3 + levelAmplitudeM3 * Math.sin(2 * Math.PI * elapsedSec / periodSec);
        driverObject.updateVariable(
                variableName,
                DataRecord.single(DOUBLE_VALUE_SCHEMA, Map.of("value", level))
        );
    }

    private void publishStatus(String variableName) {
        driverObject.updateVariable(
                variableName,
                DataRecord.single(STATUS_SCHEMA, Map.of(
                        "online", true,
                        "lastSeen", Instant.now().toString()
                ))
        );
    }

    private double elapsedSeconds() {
        return (System.currentTimeMillis() - startedAt) / 1000.0;
    }

    private void readConfig(String name, java.util.function.Consumer<String> consumer) {
        driverObject.getVariable(name).ifPresent(record -> {
            Object raw = record.firstRow().get("value");
            if (raw == null) {
                raw = record.firstRow().get("raw");
            }
            if (raw != null) {
                consumer.accept(raw.toString());
            }
        });
    }
}
