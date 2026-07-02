package com.ispf.driver.modbus;

import com.ghgande.j2mod.modbus.facade.ModbusTCPMaster;
import com.ghgande.j2mod.modbus.procimg.InputRegister;
import com.ghgande.j2mod.modbus.procimg.Register;
import com.ghgande.j2mod.modbus.procimg.SimpleRegister;
import com.ispf.core.model.DataRecord;
import com.ispf.core.model.DataSchema;
import com.ispf.core.model.FieldType;
import com.ispf.driver.DeviceDriver;
import com.ispf.driver.DriverException;
import com.ispf.driver.DriverMetadata;
import com.ispf.driver.DriverPollTimestamps;

import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Modbus TCP driver — reads/writes registers and maps them to ISPF object variables.
 * <p>
 * Point mapping format: {@code slaveId:registerType:address[:count]}
 * registerType: HOLDING, INPUT, COIL, DISCRETE
 */
public class ModbusTcpDeviceDriver implements DeviceDriver {

    private static final DriverMetadata METADATA = new DriverMetadata(
            "modbus-tcp",
            "Modbus TCP Driver",
            "0.1.0",
            "Polls Modbus TCP slaves and maps register values to ISPF variables",
            "ISPF",
            Map.of(
                    "host", "127.0.0.1",
                    "port", "502",
                    "timeoutMs", "3000",
                    "pollIntervalMs", "1000"
            )
    );

    private static final DataSchema REGISTER_SCHEMA = DataSchema.builder("modbusRegister")
            .field("value", FieldType.DOUBLE)
            .field("raw", FieldType.LONG)
            .build();

    private static final DataSchema COIL_SCHEMA = DataSchema.builder("modbusCoil")
            .field("value", FieldType.BOOLEAN)
            .build();

    private DriverObject driverObject;
    private ModbusTCPMaster master;
    private String host = "127.0.0.1";
    private int port = 502;
    private int timeoutMs = 3000;
    private final Map<String, ModbusPoint> points = new ConcurrentHashMap<>();
    private volatile boolean connected;

    @Override
    public DriverMetadata metadata() {
        return METADATA;
    }

    @Override
    public void initialize(DriverObject driverObject) {
        this.driverObject = driverObject;
        readConfig("host", value -> host = value);
        readConfig("port", value -> port = Integer.parseInt(value));
        readConfig("timeoutMs", value -> timeoutMs = Integer.parseInt(value));
    }

    @Override
    public void connect() throws DriverException {
        try {
            master = new ModbusTCPMaster(host, port, timeoutMs, false);
            master.connect();
            connected = true;
            driverObject.log(DriverLogLevel.INFO, "Connected to Modbus TCP " + host + ":" + port);
        } catch (Exception e) {
            connected = false;
            throw new DriverException("Modbus TCP connect failed", e);
        }
    }

    @Override
    public void disconnect() {
        connected = false;
        if (master != null) {
            master.disconnect();
            master = null;
        }
    }

    @Override
    public boolean isConnected() {
        return connected && master != null && master.isConnected();
    }

    @Override
    public void readPoints(Map<String, String> pointMappings) throws DriverException {
        if (!isConnected()) {
            throw new DriverException("Not connected");
        }
        points.clear();
        Instant observedAt = DriverPollTimestamps.pollTick();
        for (Map.Entry<String, String> entry : pointMappings.entrySet()) {
            ModbusPoint point = ModbusPoint.parse(entry.getValue());
            points.put(entry.getKey(), point);
            DataRecord record = readPoint(point);
            driverObject.updateVariable(entry.getKey(), record, observedAt);
        }
    }

    @Override
    public void writePoint(String pointId, DataRecord value) throws DriverException {
        if (!isConnected()) {
            throw new DriverException("Not connected");
        }
        ModbusPoint point = points.get(pointId);
        if (point == null) {
            throw new DriverException("Unknown point: " + pointId);
        }
        try {
            switch (point.type()) {
                case HOLDING -> {
                    long raw = extractNumeric(value);
                    master.writeSingleRegister(point.slaveId(), point.address(), new SimpleRegister((int) raw));
                }
                case COIL -> {
                    boolean coilValue = Boolean.TRUE.equals(value.firstRow().get("value"));
                    master.writeCoil(point.slaveId(), point.address(), coilValue);
                }
                case INPUT, DISCRETE -> throw new DriverException("Register type is read-only: " + point.type());
            }
            driverObject.updateVariable(pointId, readPoint(point), DriverPollTimestamps.pollTick());
        } catch (DriverException e) {
            throw e;
        } catch (Exception e) {
            throw new DriverException("Modbus write failed for point " + pointId, e);
        }
    }

    private DataRecord readPoint(ModbusPoint point) throws DriverException {
        try {
            return switch (point.type()) {
                case HOLDING -> {
                    Register register = master.readMultipleRegisters(point.slaveId(), point.address(), 1)[0];
                    yield toRegisterRecord(register.getValue());
                }
                case INPUT -> {
                    InputRegister register = master.readInputRegisters(point.slaveId(), point.address(), 1)[0];
                    yield toRegisterRecord(register.getValue());
                }
                case COIL -> {
                    boolean value = master.readCoils(point.slaveId(), point.address(), 1).getBit(0);
                    yield DataRecord.single(COIL_SCHEMA, Map.of("value", value));
                }
                case DISCRETE -> {
                    boolean value = master.readInputDiscretes(point.slaveId(), point.address(), 1).getBit(0);
                    yield DataRecord.single(COIL_SCHEMA, Map.of("value", value));
                }
            };
        } catch (Exception e) {
            throw new DriverException("Modbus read failed at " + point, e);
        }
    }

    private static DataRecord toRegisterRecord(int raw) {
        return DataRecord.single(REGISTER_SCHEMA, Map.of(
                "raw", (long) raw,
                "value", (double) raw
        ));
    }

    private static long extractNumeric(DataRecord value) {
        Object raw = value.firstRow().get("raw");
        if (raw instanceof Number number) {
            return number.longValue();
        }
        Object numeric = value.firstRow().get("value");
        if (numeric instanceof Number number) {
            return number.longValue();
        }
        throw new IllegalArgumentException("Modbus write requires numeric raw/value field");
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
