package com.ispf.driver.modbussimulator;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * In-process Modbus register image for loopback tests (no TCP slave).
 */
final class ModbusSimulatorMemory {

    private final ConcurrentMap<String, Long> registers = new ConcurrentHashMap<>();
    private final ConcurrentMap<String, Boolean> coils = new ConcurrentHashMap<>();

    void seedHolding(int slaveId, int address, long value) {
        registers.put(key(slaveId, ModbusSimulatorPoint.RegisterType.HOLDING, address), value);
    }

    void seedInput(int slaveId, int address, long value) {
        registers.put(key(slaveId, ModbusSimulatorPoint.RegisterType.INPUT, address), value);
    }

    void seedCoil(int slaveId, int address, boolean value) {
        coils.put(key(slaveId, ModbusSimulatorPoint.RegisterType.COIL, address), value);
    }

    void seedDiscrete(int slaveId, int address, boolean value) {
        coils.put(key(slaveId, ModbusSimulatorPoint.RegisterType.DISCRETE, address), value);
    }

    long readRegister(ModbusSimulatorPoint point) {
        return registers.computeIfAbsent(
                key(point.slaveId(), point.type(), point.address()),
                ignored -> (long) (point.address() + 1));
    }

    boolean readCoil(ModbusSimulatorPoint point) {
        return coils.computeIfAbsent(
                key(point.slaveId(), point.type(), point.address()),
                ignored -> point.address() % 2 == 0);
    }

    void writeHolding(ModbusSimulatorPoint point, long raw) {
        registers.put(key(point.slaveId(), ModbusSimulatorPoint.RegisterType.HOLDING, point.address()), raw);
    }

    void writeCoil(ModbusSimulatorPoint point, boolean value) {
        coils.put(key(point.slaveId(), ModbusSimulatorPoint.RegisterType.COIL, point.address()), value);
    }

    void clear() {
        registers.clear();
        coils.clear();
    }

    private static String key(int slaveId, ModbusSimulatorPoint.RegisterType type, int address) {
        return slaveId + ":" + type.name() + ":" + address;
    }
}
