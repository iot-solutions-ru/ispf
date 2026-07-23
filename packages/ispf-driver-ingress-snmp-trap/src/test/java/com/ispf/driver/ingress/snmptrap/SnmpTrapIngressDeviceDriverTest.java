package com.ispf.driver.ingress.snmptrap;

import com.ispf.core.model.DataRecord;
import com.ispf.core.object.ObjectType;
import com.ispf.core.object.PlatformObject;
import com.ispf.driver.DeviceDriver;
import com.ispf.driver.DriverException;
import org.junit.jupiter.api.Test;

import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.time.Instant;
import java.util.Base64;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SnmpTrapIngressDeviceDriverTest {

    private static final byte[] TRAP_PAYLOAD = {0x30, (byte) 0x82, 0x01, 0x02, 0x7f};

    @Test
    void connectsWithConfiguredPort() throws Exception {
        StubDriverObject driverObject = new StubDriverObject(Map.of("port", "0"));
        SnmpTrapIngressDeviceDriver driver = new SnmpTrapIngressDeviceDriver();
        driver.initialize(driverObject);
        driver.connect();
        assertTrue(driver.isConnected());
        driver.disconnect();
    }

    @Test
    void readPointsRequiresConnection() {
        SnmpTrapIngressDeviceDriver driver = new SnmpTrapIngressDeviceDriver();
        driver.initialize(new StubDriverObject(Map.of("port", "0")));
        assertThrows(
                DriverException.class,
                () -> driver.readPoints(Map.of("stats", "snmpTrap"))
        );
    }

    @Test
    void publishesTrapPayloadAndStats() throws Exception {
        int port = freeUdpPort();
        StubDriverObject driverObject = new StubDriverObject(Map.of("port", String.valueOf(port)));
        SnmpTrapIngressDeviceDriver driver = new SnmpTrapIngressDeviceDriver();
        driver.initialize(driverObject);
        driver.connect();
        try {
            try (DatagramSocket sender = new DatagramSocket()) {
                sender.send(new DatagramPacket(TRAP_PAYLOAD, TRAP_PAYLOAD.length,
                        InetAddress.getByName("127.0.0.1"), port));
            }

            DataRecord record = awaitVariable(driverObject, "lastTrap");
            assertEquals(Base64.getEncoder().encodeToString(TRAP_PAYLOAD), record.firstRow().get("payloadBase64"));
            assertEquals("308201027f", record.firstRow().get("payloadHex"));
            assertEquals("127.0.0.1", record.firstRow().get("sourceHost"));
            assertEquals(TRAP_PAYLOAD.length, record.firstRow().get("bytes"));
            assertNotNull(driverObject.observedAt.get("lastTrap"));

            driver.readPoints(Map.of("stats", "snmpTrap"));
            DataRecord stats = driverObject.variables.get("stats");
            assertEquals(1L, stats.firstRow().get("trapsReceived"));
            assertEquals(true, stats.firstRow().get("listening"));
        } finally {
            driver.disconnect();
        }
    }

    @Test
    void bindsOnLoopbackAddress() throws Exception {
        int port = freeUdpPort();
        StubDriverObject driverObject = new StubDriverObject(Map.of(
                "port", String.valueOf(port),
                "bindAddress", "127.0.0.1"
        ));
        SnmpTrapIngressDeviceDriver driver = new SnmpTrapIngressDeviceDriver();
        driver.initialize(driverObject);
        driver.connect();
        try {
            assertTrue(driver.isConnected());
            try (DatagramSocket sender = new DatagramSocket()) {
                sender.send(new DatagramPacket(TRAP_PAYLOAD, TRAP_PAYLOAD.length,
                        InetAddress.getByName("127.0.0.1"), port));
            }

            DataRecord record = awaitVariable(driverObject, "lastTrap");
            assertEquals(Base64.getEncoder().encodeToString(TRAP_PAYLOAD), record.firstRow().get("payloadBase64"));
            assertEquals("127.0.0.1", record.firstRow().get("sourceHost"));
            assertNotNull(driverObject.observedAt.get("lastTrap"));
        } finally {
            driver.disconnect();
        }
    }

    @Test
    void writePointIsReadOnly() throws Exception {
        SnmpTrapIngressDeviceDriver driver = new SnmpTrapIngressDeviceDriver();
        driver.initialize(new StubDriverObject(Map.of("port", "0")));
        driver.connect();
        try {
            assertThrows(DriverException.class, () -> driver.writePoint("stats", null));
        } finally {
            driver.disconnect();
        }
    }

    private static int freeUdpPort() throws Exception {
        try (DatagramSocket socket = new DatagramSocket(0)) {
            return socket.getLocalPort();
        }
    }

    private static DataRecord awaitVariable(StubDriverObject driverObject, String name) throws Exception {
        long deadline = System.nanoTime() + 5_000_000_000L;
        while (System.nanoTime() < deadline) {
            DataRecord record = driverObject.variables.get(name);
            if (record != null) {
                return record;
            }
            Thread.sleep(10);
        }
        throw new AssertionError("Timed out waiting for variable " + name);
    }

    private static final class StubDriverObject implements DeviceDriver.DriverObject {
        private final Map<String, String> config;
        private final Map<String, DataRecord> variables = new ConcurrentHashMap<>();
        private final Map<String, Instant> observedAt = new ConcurrentHashMap<>();

        private StubDriverObject(Map<String, String> config) {
            this.config = config;
        }

        @Override
        public PlatformObject deviceObject() {
            return new PlatformObject(
                    "ingress-snmp-trap-test",
                    "root.platform.devices.itm.ingress.snmptrap",
                    ObjectType.DEVICE,
                    "SnmpTrap",
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
