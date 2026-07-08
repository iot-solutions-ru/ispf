package com.ispf.driver.modbussimulator;

import com.ispf.core.model.DataRecord;
import com.ispf.core.model.DataSchema;
import com.ispf.core.model.FieldType;
import com.ispf.core.object.ObjectType;
import com.ispf.core.object.PlatformObject;
import com.ispf.driver.DeviceDriver;
import com.ispf.driver.DriverException;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ModbusSimulatorDeviceDriverTest {

    private static final DataSchema REGISTER_SCHEMA = DataSchema.builder("modbusRegister")
            .field("value", FieldType.DOUBLE)
            .field("raw", FieldType.LONG)
            .build();

    @Test
    void readsSeededHoldingRegister() throws Exception {
        StubDriverObject driverObject = new StubDriverObject(Map.of("seedHolding0", "42"));
        ModbusSimulatorDeviceDriver driver = new ModbusSimulatorDeviceDriver();
        driver.initialize(driverObject);
        driver.connect();
        driver.readPoints(Map.of("setpoint", "1:HOLDING:0"));

        assertEquals(42L, driverObject.variables.get("setpoint").firstRow().get("raw"));
        driver.disconnect();
    }

    @Test
    void writeHoldingRegisterUpdatesVariable() throws Exception {
        StubDriverObject driverObject = new StubDriverObject(Map.of());
        ModbusSimulatorDeviceDriver driver = new ModbusSimulatorDeviceDriver();
        driver.initialize(driverObject);
        driver.connect();
        driver.readPoints(Map.of("setpoint", "1:HOLDING:0"));
        driver.writePoint("setpoint", DataRecord.single(REGISTER_SCHEMA, Map.of("raw", 77L, "value", 77.0)));

        assertEquals(77L, driverObject.variables.get("setpoint").firstRow().get("raw"));
        driver.disconnect();
    }

    @Test
    void rejectsInvalidMapping() {
        assertThrows(IllegalArgumentException.class, () -> ModbusSimulatorPoint.parse("bad"));
    }

    @Test
    void rejectsWriteToInputRegister() throws Exception {
        StubDriverObject driverObject = new StubDriverObject(Map.of());
        ModbusSimulatorDeviceDriver driver = new ModbusSimulatorDeviceDriver();
        driver.initialize(driverObject);
        driver.connect();
        driver.readPoints(Map.of("sensor", "1:INPUT:0"));

        assertThrows(DriverException.class, () ->
                driver.writePoint("sensor", DataRecord.single(REGISTER_SCHEMA, Map.of("raw", 1L, "value", 1.0))));
        driver.disconnect();
    }

    private static final class StubDriverObject implements DeviceDriver.DriverObject {
        private final Map<String, String> config;
        private final Map<String, DataRecord> variables = new HashMap<>();

        private StubDriverObject(Map<String, String> config) {
            this.config = config;
        }

        @Override
        public PlatformObject deviceObject() {
            return new PlatformObject(
                    "modbus-simulator",
                    "root.platform.devices.modbus-simulator",
                    ObjectType.DEVICE,
                    "Modbus Simulator",
                    "",
                    null);
        }

        @Override
        public void updateVariable(String name, DataRecord value) {
            variables.put(name, value);
        }

        @Override
        public Optional<DataRecord> getVariable(String name) {
            return Optional.ofNullable(variables.get(name));
        }

        @Override
        public void log(DeviceDriver.DriverLogLevel level, String message) {
        }

        @Override
        public Map<String, String> configuration() {
            return config;
        }
    }
}
