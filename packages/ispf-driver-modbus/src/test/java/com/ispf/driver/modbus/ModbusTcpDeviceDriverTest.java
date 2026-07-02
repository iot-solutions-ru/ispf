package com.ispf.driver.modbus;

import com.ghgande.j2mod.modbus.procimg.SimpleDigitalIn;
import com.ghgande.j2mod.modbus.procimg.SimpleDigitalOut;
import com.ghgande.j2mod.modbus.procimg.SimpleInputRegister;
import com.ghgande.j2mod.modbus.procimg.SimpleProcessImage;
import com.ghgande.j2mod.modbus.procimg.SimpleRegister;
import com.ghgande.j2mod.modbus.slave.ModbusSlave;
import com.ghgande.j2mod.modbus.slave.ModbusSlaveFactory;
import com.ispf.core.model.DataRecord;
import com.ispf.core.model.DataSchema;
import com.ispf.core.model.FieldType;
import com.ispf.core.object.ObjectType;
import com.ispf.core.object.PlatformObject;
import com.ispf.driver.DeviceDriver;
import com.ispf.driver.DriverException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.net.ServerSocket;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ModbusTcpDeviceDriverTest {

    private static final DataSchema REGISTER_SCHEMA = DataSchema.builder("modbusRegister")
            .field("value", FieldType.DOUBLE)
            .field("raw", FieldType.LONG)
            .build();

    private static final DataSchema COIL_SCHEMA = DataSchema.builder("modbusCoil")
            .field("value", FieldType.BOOLEAN)
            .build();

    @AfterEach
    void closeSlaves() {
        ModbusSlaveFactory.close();
    }

    @Test
    void readsHoldingRegisterFromTcpSlave() throws Exception {
        int port = freePort();
        startTcpSlave(port, holdingImage(42));

        StubDriverObject driverObject = new StubDriverObject(Map.of(
                "host", "127.0.0.1",
                "port", String.valueOf(port),
                "timeoutMs", "3000"
        ));
        ModbusTcpDeviceDriver driver = new ModbusTcpDeviceDriver();
        driver.initialize(driverObject);
        driver.connect();
        driver.readPoints(Map.of("setpoint", "1:HOLDING:0"));

        DataRecord record = driverObject.variables.get("setpoint");
        assertEquals(42L, record.firstRow().get("raw"));
        assertEquals(42.0, record.firstRow().get("value"));
        assertNotNull(driverObject.observedAt.get("setpoint"));
        driver.disconnect();
    }

    @Test
    void readPointsUsesSharedObservedAtPerTick() throws Exception {
        int port = freePort();
        startTcpSlave(port, holdingImageWithTwoRegisters(10, 20));

        StubDriverObject driverObject = new StubDriverObject(Map.of(
                "host", "127.0.0.1",
                "port", String.valueOf(port),
                "timeoutMs", "3000"
        ));
        ModbusTcpDeviceDriver driver = new ModbusTcpDeviceDriver();
        driver.initialize(driverObject);
        driver.connect();
        driver.readPoints(Map.of(
                "a", "1:HOLDING:0",
                "b", "1:HOLDING:1"
        ));

        Instant a = driverObject.observedAt.get("a");
        Instant b = driverObject.observedAt.get("b");
        assertNotNull(a);
        assertEquals(a, b);
        driver.disconnect();
    }

    @Test
    void writeHoldingRegisterUpdatesSlaveAndVariable() throws Exception {
        int port = freePort();
        SimpleProcessImage image = holdingImage(100);
        startTcpSlave(port, image);

        StubDriverObject driverObject = new StubDriverObject(Map.of(
                "host", "127.0.0.1",
                "port", String.valueOf(port),
                "timeoutMs", "3000"
        ));
        ModbusTcpDeviceDriver driver = new ModbusTcpDeviceDriver();
        driver.initialize(driverObject);
        driver.connect();
        driver.readPoints(Map.of("setpoint", "1:HOLDING:0"));

        driver.writePoint("setpoint", DataRecord.single(REGISTER_SCHEMA, Map.of("raw", 250L, "value", 250.0)));

        assertEquals(250, image.getRegister(0).getValue());
        assertEquals(250L, driverObject.variables.get("setpoint").firstRow().get("raw"));
        driver.disconnect();
    }

    @Test
    void writeCoilUpdatesSlaveAndVariable() throws Exception {
        int port = freePort();
        SimpleProcessImage image = coilImage(false);
        startTcpSlave(port, image);

        StubDriverObject driverObject = new StubDriverObject(Map.of(
                "host", "127.0.0.1",
                "port", String.valueOf(port),
                "timeoutMs", "3000"
        ));
        ModbusTcpDeviceDriver driver = new ModbusTcpDeviceDriver();
        driver.initialize(driverObject);
        driver.connect();
        driver.readPoints(Map.of("relay", "1:COIL:0"));

        driver.writePoint("relay", DataRecord.single(COIL_SCHEMA, Map.of("value", true)));

        assertTrue(image.getDigitalOut(0).isSet());
        assertEquals(true, driverObject.variables.get("relay").firstRow().get("value"));
        driver.disconnect();
    }

    @Test
    void rejectsWriteToReadOnlyRegisterTypes() throws Exception {
        int port = freePort();
        startTcpSlave(port, fullImage());

        StubDriverObject driverObject = new StubDriverObject(Map.of(
                "host", "127.0.0.1",
                "port", String.valueOf(port),
                "timeoutMs", "3000"
        ));
        ModbusTcpDeviceDriver driver = new ModbusTcpDeviceDriver();
        driver.initialize(driverObject);
        driver.connect();
        driver.readPoints(Map.of(
                "inputReg", "1:INPUT:0",
                "discrete", "1:DISCRETE:0"
        ));

        assertThrows(DriverException.class, () ->
                driver.writePoint("inputReg", DataRecord.single(REGISTER_SCHEMA, Map.of("raw", 1L, "value", 1.0))));
        assertThrows(DriverException.class, () ->
                driver.writePoint("discrete", DataRecord.single(COIL_SCHEMA, Map.of("value", true))));
        driver.disconnect();
    }

    @Test
    void rejectsUnknownPoint() throws Exception {
        int port = freePort();
        startTcpSlave(port, holdingImage(0));

        StubDriverObject driverObject = new StubDriverObject(Map.of(
                "host", "127.0.0.1",
                "port", String.valueOf(port),
                "timeoutMs", "3000"
        ));
        ModbusTcpDeviceDriver driver = new ModbusTcpDeviceDriver();
        driver.initialize(driverObject);
        driver.connect();

        DriverException error = assertThrows(DriverException.class, () ->
                driver.writePoint("missing", DataRecord.single(REGISTER_SCHEMA, Map.of("raw", 1L, "value", 1.0))));
        assertTrue(error.getMessage().contains("Unknown point"));
        driver.disconnect();
    }

    private static int freePort() throws Exception {
        try (ServerSocket socket = new ServerSocket(0)) {
            return socket.getLocalPort();
        }
    }

    private static void startTcpSlave(int port, SimpleProcessImage image) throws Exception {
        ModbusSlave slave = ModbusSlaveFactory.createTCPSlave(port, 3);
        slave.addProcessImage(1, image);
        slave.open();
    }

    private static SimpleProcessImage holdingImageWithTwoRegisters(int first, int second) {
        SimpleProcessImage image = new SimpleProcessImage();
        image.addRegister(new SimpleRegister(first));
        image.addRegister(new SimpleRegister(second));
        return image;
    }

    private static SimpleProcessImage holdingImage(int initialValue) {
        SimpleProcessImage image = new SimpleProcessImage();
        image.addRegister(new SimpleRegister(initialValue));
        return image;
    }

    private static SimpleProcessImage coilImage(boolean initialValue) {
        SimpleProcessImage image = new SimpleProcessImage();
        image.addDigitalOut(new SimpleDigitalOut(initialValue));
        return image;
    }

    private static SimpleProcessImage fullImage() {
        SimpleProcessImage image = new SimpleProcessImage();
        image.addRegister(new SimpleRegister(0));
        image.addInputRegister(new SimpleInputRegister(0));
        image.addDigitalOut(new SimpleDigitalOut(false));
        image.addDigitalIn(new SimpleDigitalIn(false));
        return image;
    }

    private static final class StubDriverObject implements DeviceDriver.DriverObject {

        private static final DataSchema STRING_VALUE = DataSchema.builder("stringValue")
                .field("value", FieldType.STRING)
                .field("raw", FieldType.STRING)
                .build();

        private final Map<String, String> configuration;
        private final Map<String, DataRecord> variables = new HashMap<>();
        private final Map<String, Instant> observedAt = new HashMap<>();

        StubDriverObject(Map<String, String> configuration) {
            this.configuration = configuration;
        }

        @Override
        public PlatformObject deviceObject() {
            return new PlatformObject(
                    "test-device",
                    "root.platform.devices.test",
                    ObjectType.DEVICE,
                    "Test",
                    "",
                    null
            );
        }

        @Override
        public void updateVariable(String name, DataRecord value) {
            variables.put(name, value);
        }

        @Override
        public void updateVariable(String name, DataRecord value, Instant observedAt) {
            variables.put(name, value);
            if (observedAt != null) {
                this.observedAt.put(name, observedAt);
            }
        }

        @Override
        public Optional<DataRecord> getVariable(String name) {
            if (configuration.containsKey(name)) {
                String value = configuration.get(name);
                return Optional.of(DataRecord.single(STRING_VALUE, Map.of("value", value, "raw", value)));
            }
            return Optional.ofNullable(variables.get(name));
        }

        @Override
        public void log(DeviceDriver.DriverLogLevel level, String message) {
        }

        @Override
        public Map<String, String> configuration() {
            return configuration;
        }
    }
}
