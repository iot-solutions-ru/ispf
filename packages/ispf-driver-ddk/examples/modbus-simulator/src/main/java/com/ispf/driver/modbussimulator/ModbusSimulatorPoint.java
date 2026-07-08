package com.ispf.driver.modbussimulator;

/**
 * Parses point mapping {@code slaveId:type:address[:count]} (Modbus TCP convention).
 */
public record ModbusSimulatorPoint(int slaveId, RegisterType type, int address, int count) {

    enum RegisterType {
        HOLDING, INPUT, COIL, DISCRETE
    }

    public static ModbusSimulatorPoint parse(String mapping) {
        if (mapping == null || mapping.isBlank()) {
            throw new IllegalArgumentException("point mapping required");
        }
        String[] parts = mapping.split(":");
        if (parts.length < 3) {
            throw new IllegalArgumentException("expected slaveId:type:address, got: " + mapping);
        }
        try {
            int slaveId = Integer.parseInt(parts[0].trim());
            RegisterType type = RegisterType.valueOf(parts[1].trim().toUpperCase());
            int address = Integer.parseInt(parts[2].trim());
            int count = parts.length > 3 ? Integer.parseInt(parts[3].trim()) : 1;
            return new ModbusSimulatorPoint(slaveId, type, address, count);
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("invalid Modbus mapping: " + mapping, e);
        }
    }
}
