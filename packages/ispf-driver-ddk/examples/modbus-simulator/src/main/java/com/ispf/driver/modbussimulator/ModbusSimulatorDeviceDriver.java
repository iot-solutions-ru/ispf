package com.ispf.driver.modbussimulator;

import com.ispf.core.model.DataRecord;
import com.ispf.core.model.DataSchema;
import com.ispf.core.model.FieldType;
import com.ispf.driver.DeviceDriver;
import com.ispf.driver.DriverException;
import com.ispf.driver.DriverMaturity;
import com.ispf.driver.DriverMetadata;
import com.ispf.driver.DriverPollTimestamps;

import java.time.Instant;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Reference DDK driver — in-memory Modbus register simulator (BL-144 wave 8).
 * <p>
 * Point mapping format matches production {@code modbus-tcp}: {@code slaveId:type:address}.
 */
public class ModbusSimulatorDeviceDriver implements DeviceDriver {

    private static final DataSchema REGISTER_SCHEMA = DataSchema.builder("modbusRegister")
            .field("value", FieldType.DOUBLE)
            .field("raw", FieldType.LONG)
            .build();

    private static final DataSchema COIL_SCHEMA = DataSchema.builder("modbusCoil")
            .field("value", FieldType.BOOLEAN)
            .build();

    private static final DriverMetadata METADATA = new DriverMetadata(
            "modbus-simulator",
            "Modbus Simulator Driver (DDK reference)",
            "0.1.0",
            "In-process Modbus register image for CI loopback — no external slave",
            "ISPF",
            Map.of(
                    "seedHolding0", "100",
                    "pollIntervalMs", "1000"
            ),
            DriverMaturity.BETA,
            Set.of("read", "write")
    );

    private DriverObject driverObject;
    private final ModbusSimulatorMemory memory = new ModbusSimulatorMemory();
    private final Map<String, ModbusSimulatorPoint> points = new ConcurrentHashMap<>();
    private int seedHolding0 = 100;
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
        if ("seedHolding0".equals(key)) {
            seedHolding0 = Integer.parseInt(value.trim());
        }
    }

    @Override
    public void connect() throws DriverException {
        memory.clear();
        memory.seedHolding(1, 0, seedHolding0);
        connected = true;
        driverObject.log(DriverLogLevel.INFO, "Modbus simulator connected (in-memory slave 1)");
    }

    @Override
    public void disconnect() {
        connected = false;
        points.clear();
        memory.clear();
    }

    @Override
    public boolean isConnected() {
        return connected;
    }

    @Override
    public void readPoints(Map<String, String> pointMappings) throws DriverException {
        requireConnected();
        Instant observedAt = DriverPollTimestamps.pollTick();
        for (Map.Entry<String, String> entry : pointMappings.entrySet()) {
            ModbusSimulatorPoint point = ModbusSimulatorPoint.parse(entry.getValue());
            points.put(entry.getKey(), point);
            driverObject.updateVariable(entry.getKey(), readPoint(point), observedAt);
        }
    }

    @Override
    public void writePoint(String pointId, DataRecord value) throws DriverException {
        requireConnected();
        ModbusSimulatorPoint point = points.get(pointId);
        if (point == null) {
            throw new DriverException("Unknown point: " + pointId);
        }
        switch (point.type()) {
            case HOLDING -> memory.writeHolding(point, extractNumeric(value));
            case COIL -> memory.writeCoil(point, Boolean.TRUE.equals(value.firstRow().get("value")));
            case INPUT, DISCRETE -> throw new DriverException("Register type is read-only: " + point.type());
        }
        driverObject.updateVariable(pointId, readPoint(point), DriverPollTimestamps.pollTick());
    }

    private DataRecord readPoint(ModbusSimulatorPoint point) throws DriverException {
        return switch (point.type()) {
            case HOLDING, INPUT -> {
                long raw = memory.readRegister(point);
                yield DataRecord.single(REGISTER_SCHEMA, Map.of("raw", raw, "value", (double) raw));
            }
            case COIL, DISCRETE -> {
                boolean coil = memory.readCoil(point);
                yield DataRecord.single(COIL_SCHEMA, Map.of("value", coil));
            }
        };
    }

    private static long extractNumeric(DataRecord value) throws DriverException {
        Object raw = value.firstRow().get("raw");
        if (raw instanceof Number number) {
            return number.longValue();
        }
        Object numeric = value.firstRow().get("value");
        if (numeric instanceof Number number) {
            return number.longValue();
        }
        throw new DriverException("Expected numeric raw or value field");
    }

    private void requireConnected() throws DriverException {
        if (!connected) {
            throw new DriverException("Not connected");
        }
    }
}
