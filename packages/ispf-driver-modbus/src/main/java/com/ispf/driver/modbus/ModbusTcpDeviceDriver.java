package com.ispf.driver.modbus;

import com.ghgande.j2mod.modbus.facade.ModbusTCPMaster;
import com.ghgande.j2mod.modbus.procimg.InputRegister;
import com.ghgande.j2mod.modbus.procimg.Register;
import com.ghgande.j2mod.modbus.procimg.SimpleRegister;
import com.ispf.core.model.DataRecord;
import com.ispf.core.model.DataSchema;
import com.ispf.core.model.FieldType;
import com.ispf.driver.DeviceDriver;
import com.ispf.driver.DriverConfigurationException;
import com.ispf.driver.DriverException;
import com.ispf.driver.DriverMetadata;
import com.ispf.driver.DriverPollTimestamps;
import com.ispf.driver.DriverTransientException;
import com.ispf.driver.DriverUnsupportedOperationException;

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
            throw new DriverTransientException("Modbus TCP connect failed", e);
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
            throw new DriverTransientException("Not connected");
        }
        Map<String, ModbusPoint> next = new ConcurrentHashMap<>();
        Instant observedAt = DriverPollTimestamps.pollTick();
        for (Map.Entry<String, String> entry : pointMappings.entrySet()) {
            ModbusPoint point = ModbusPoint.parse(entry.getValue());
            next.put(entry.getKey(), point);
            DataRecord record = readPoint(point);
            driverObject.updateVariable(entry.getKey(), record, observedAt);
        }
        replacePoints(next);
    }

    @Override
    public void writePoint(String pointId, DataRecord value) throws DriverException {
        if (!isConnected()) {
            throw new DriverTransientException("Not connected");
        }
        ModbusPoint point = points.get(pointId);
        if (point == null) {
            throw new DriverConfigurationException("Unknown point: " + pointId);
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
                case INPUT, DISCRETE -> throw new DriverUnsupportedOperationException("Register type is read-only: " + point.type());
            }
            driverObject.updateVariable(pointId, readPoint(point), DriverPollTimestamps.pollTick());
        } catch (DriverException e) {
            throw e;
        } catch (Exception e) {
            throw new DriverTransientException("Modbus write failed for point " + pointId, e);
        }
    }

    private void replacePoints(Map<String, ModbusPoint> next) {
        points.keySet().retainAll(next.keySet());
        points.putAll(next);
    }

    private DataRecord readPoint(ModbusPoint point) throws DriverException {
        try {
            int count = Math.max(1, point.count());
            return switch (point.type()) {
                case HOLDING -> {
                    Register[] registers = master.readMultipleRegisters(point.slaveId(), point.address(), count);
                    yield toRegisterRecord(combineRegisters(registers));
                }
                case INPUT -> {
                    InputRegister[] registers = master.readInputRegisters(point.slaveId(), point.address(), count);
                    yield toRegisterRecord(combineInputRegisters(registers));
                }
                case COIL -> {
                    boolean value = master.readCoils(point.slaveId(), point.address(), count).getBit(0);
                    yield DataRecord.single(COIL_SCHEMA, Map.of("value", value));
                }
                case DISCRETE -> {
                    boolean value = master.readInputDiscretes(point.slaveId(), point.address(), count).getBit(0);
                    yield DataRecord.single(COIL_SCHEMA, Map.of("value", value));
                }
            };
        } catch (Exception e) {
            throw new DriverTransientException("Modbus read failed at " + point, e);
        }
    }

    private static long combineRegisters(Register[] registers) {
        long raw = 0L;
        for (Register register : registers) {
            raw = (raw << 16) | (register.getValue() & 0xFFFFL);
        }
        return raw;
    }

    private static long combineInputRegisters(InputRegister[] registers) {
        long raw = 0L;
        for (InputRegister register : registers) {
            raw = (raw << 16) | (register.getValue() & 0xFFFFL);
        }
        return raw;
    }

    private static DataRecord toRegisterRecord(long raw) {
        return DataRecord.single(REGISTER_SCHEMA, Map.of(
                "raw", raw,
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
        // Prefer binding configuration() (driverConfigJson keys). Fall back to device variables
        // for older packs / manual host/port variables used in lab soaks.
        String fromBinding = driverObject.configuration().get(name);
        if (fromBinding != null && !fromBinding.isBlank()) {
            consumer.accept(fromBinding.trim());
            return;
        }
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
