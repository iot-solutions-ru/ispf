package com.ispf.driver.modbusudp;

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
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.net.DatagramSocket;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Loopback test against a j2mod UDP slave on 127.0.0.1 (MBAP + PDU datagrams, FC1/FC2/FC3/FC4/FC5/FC6).
 * The interop lab ({@code deploy/driver-interop}, service {@code modbus-udp}) covers the same wire
 * format against an independent Python fixture.
 */
class ModbusUdpDeviceDriverTest {

    private static final DataSchema REGISTER_SCHEMA = DataSchema.builder("modbusRegister")
            .field("value", FieldType.DOUBLE)
            .field("raw", FieldType.LONG)
            .build();

    private static final DataSchema COIL_SCHEMA = DataSchema.builder("modbusCoil")
            .field("value", FieldType.BOOLEAN)
            .build();

    /** One UDP slave per class; each test owns a unit id so process images never overlap. */
    private static int slavePort;
    private static ModbusSlave slave;

    @BeforeAll
    static void startSlave() throws Exception {
        slavePort = freePort();
        slave = ModbusSlaveFactory.createUDPSlave(slavePort);
        slave.open();
    }

    @AfterAll
    static void stopSlave() {
        try {
            ModbusSlaveFactory.close();
        } catch (UnsupportedOperationException ignored) {
            // j2mod 3.3.0: the UDP listener thread outlives stop() and the factory falls back to
            // Thread.stop(), which JDK 20+ rejects. The socket is already closed at this point.
        }
    }

    @Test
    void readsAllRegisterTypesFromUdpSlave() throws Exception {
        int unit = 1;
        SimpleProcessImage image = new SimpleProcessImage();
        image.addRegister(new SimpleRegister(42));
        image.addInputRegister(new SimpleInputRegister(7));
        image.addDigitalOut(new SimpleDigitalOut(true));
        image.addDigitalIn(new SimpleDigitalIn(false));
        slave.addProcessImage(unit, image);

        StubDriverObject driverObject = stub(slavePort);
        ModbusUdpDeviceDriver driver = new ModbusUdpDeviceDriver();
        driver.initialize(driverObject);
        driver.connect();
        assertTrue(driver.isConnected());
        driver.readPoints(Map.of(
                "holding", unit + ":HOLDING:0",
                "input", unit + ":INPUT:0",
                "coil", unit + ":COIL:0",
                "discrete", unit + ":DISCRETE:0"
        ));

        assertEquals(42L, driverObject.variables.get("holding").firstRow().get("raw"));
        assertEquals(42.0, driverObject.variables.get("holding").firstRow().get("value"));
        assertEquals(7L, driverObject.variables.get("input").firstRow().get("raw"));
        assertEquals(true, driverObject.variables.get("coil").firstRow().get("value"));
        assertEquals(false, driverObject.variables.get("discrete").firstRow().get("value"));

        Instant tick = driverObject.observedAt.get("holding");
        assertNotNull(tick, "poll drivers must stamp observedAt");
        assertEquals(tick, driverObject.observedAt.get("coil"), "one observedAt per poll tick");
        driver.disconnect();
        assertFalse(driver.isConnected());
    }

    @Test
    void writesHoldingRegisterAndCoilThroughUdp() throws Exception {
        int unit = 2;
        SimpleProcessImage image = new SimpleProcessImage();
        image.addRegister(new SimpleRegister(100));
        image.addDigitalOut(new SimpleDigitalOut(false));
        slave.addProcessImage(unit, image);

        StubDriverObject driverObject = stub(slavePort);
        ModbusUdpDeviceDriver driver = new ModbusUdpDeviceDriver();
        driver.initialize(driverObject);
        driver.connect();
        driver.readPoints(Map.of("setpoint", unit + ":HOLDING:0", "relay", unit + ":COIL:0"));

        driver.writePoint("setpoint", DataRecord.single(REGISTER_SCHEMA, Map.of("raw", 250L, "value", 250.0)));
        driver.writePoint("relay", DataRecord.single(COIL_SCHEMA, Map.of("value", true)));

        assertEquals(250, image.getRegister(0).getValue(), "FC6 must reach the slave process image");
        assertTrue(image.getDigitalOut(0).isSet(), "FC5 must reach the slave process image");
        assertEquals(250L, driverObject.variables.get("setpoint").firstRow().get("raw"), "read-back after write");
        assertEquals(true, driverObject.variables.get("relay").firstRow().get("value"));
        driver.disconnect();
    }

    @Test
    void rejectsWritesToReadOnlyTypesAndUnknownPoints() throws Exception {
        int unit = 3;
        SimpleProcessImage image = new SimpleProcessImage();
        image.addInputRegister(new SimpleInputRegister(0));
        image.addDigitalIn(new SimpleDigitalIn(false));
        slave.addProcessImage(unit, image);

        StubDriverObject driverObject = stub(slavePort);
        ModbusUdpDeviceDriver driver = new ModbusUdpDeviceDriver();
        driver.initialize(driverObject);
        driver.connect();
        driver.readPoints(Map.of("inputReg", unit + ":INPUT:0", "discrete", unit + ":DISCRETE:0"));

        assertThrows(DriverException.class, () ->
                driver.writePoint("inputReg", DataRecord.single(REGISTER_SCHEMA, Map.of("raw", 1L, "value", 1.0))));
        assertThrows(DriverException.class, () ->
                driver.writePoint("discrete", DataRecord.single(COIL_SCHEMA, Map.of("value", true))));
        DriverException unknown = assertThrows(DriverException.class, () ->
                driver.writePoint("missing", DataRecord.single(REGISTER_SCHEMA, Map.of("raw", 1L, "value", 1.0))));
        assertTrue(unknown.getMessage().contains("Unknown point"));
        driver.disconnect();
    }

    @Test
    void readFailsWhenSlaveDoesNotAnswer() throws Exception {
        int port = freePort(); // nothing listens here: UDP "connect" succeeds, the read must time out
        StubDriverObject driverObject = new StubDriverObject(Map.of(
                "host", "127.0.0.1",
                "port", String.valueOf(port),
                "timeoutMs", "300"
        ));
        ModbusUdpDeviceDriver driver = new ModbusUdpDeviceDriver();
        driver.initialize(driverObject);
        driver.connect();

        DriverException error = assertThrows(DriverException.class, () ->
                driver.readPoints(Map.of("holding", "1:HOLDING:0")));
        assertTrue(error.getMessage().contains("read failed"), error.getMessage());
        driver.disconnect();
    }

    @Test
    void operationsRequireConnection() {
        ModbusUdpDeviceDriver driver = new ModbusUdpDeviceDriver();
        driver.initialize(stub(1502));
        assertThrows(DriverException.class, () -> driver.readPoints(Map.of("h", "1:HOLDING:0")));
        assertThrows(DriverException.class, () ->
                driver.writePoint("h", DataRecord.single(REGISTER_SCHEMA, Map.of("raw", 1L, "value", 1.0))));
    }

    private static StubDriverObject stub(int port) {
        return new StubDriverObject(Map.of(
                "host", "127.0.0.1",
                "port", String.valueOf(port),
                "timeoutMs", "3000"
        ));
    }

    private static int freePort() throws Exception {
        try (DatagramSocket socket = new DatagramSocket(0)) {
            return socket.getLocalPort();
        }
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
