package com.ispf.driver.ipmi;

import com.ispf.core.model.DataRecord;
import com.ispf.core.model.DataSchema;
import com.ispf.core.model.FieldType;
import com.ispf.core.object.ObjectType;
import com.ispf.core.object.PlatformObject;
import com.ispf.driver.DeviceDriver;
import com.ispf.driver.DriverException;
import com.ispf.driver.ipmi.codec.IpmiLanClient;
import com.ispf.driver.ipmi.codec.IpmiSdrRecord;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Loopback tests against in-test UDP peers answering RMCP pings and minimal IPMI LAN commands.
 */
class IpmiDeviceDriverTest {

    @Test
    void rmcpPingLoopbackReportsReachable() throws Exception {
        try (RmcpPeer peer = new RmcpPeer()) {
            StubDriverObject driverObject = driverConfig(peer.port(), 500);
            IpmiDeviceDriver driver = new IpmiDeviceDriver();
            driver.initialize(driverObject);
            driver.connect();
            assertTrue(driver.isConnected());

            driver.readPoints(Map.of("status", "power"));

            DataRecord record = driverObject.variables.get("status");
            assertEquals("reachable", record.firstRow().get("value"));
            assertEquals(true, record.firstRow().get("reachable"));
            assertEquals(false, record.firstRow().get("powerOn"));
            assertEquals("", record.firstRow().get("sensor"));
            assertTrue(peer.requestCount() > 0, "RMCP peer must have seen a ping request");
            driver.disconnect();
            assertFalse(driver.isConnected());
        }
    }

    @Test
    void silentPeerReportsUnreachableVariableWithoutException() throws Exception {
        StubDriverObject driverObject;
        IpmiDeviceDriver driver = new IpmiDeviceDriver();
        try (RmcpPeer peer = new RmcpPeer()) {
            driverObject = driverConfig(peer.port(), 300);
            driver.initialize(driverObject);
            driver.connect();
            assertTrue(driver.isConnected());
            peer.stopResponding();

            driver.readPoints(Map.of("status", "power"));

            DataRecord record = driverObject.variables.get("status");
            assertEquals("unreachable", record.firstRow().get("value"));
            assertEquals(false, record.firstRow().get("reachable"));
            assertEquals(false, record.firstRow().get("powerOn"));
        }
        driver.disconnect();
    }

    @Test
    void connectToSilentHostFailsWithoutCredentials() throws Exception {
        int closedPort;
        try (DatagramSocket socket = new DatagramSocket(0)) {
            closedPort = socket.getLocalPort();
        }
        StubDriverObject driverObject = driverConfig(closedPort, 300);
        IpmiDeviceDriver driver = new IpmiDeviceDriver();
        driver.initialize(driverObject);

        DriverException error = assertThrows(DriverException.class, driver::connect);
        assertTrue(error.getMessage().contains("unreachable"));
        assertFalse(driver.isConnected());
    }

    @Test
    void authenticatedSessionReadsPowerAndSensorFromMockBmc() throws Exception {
        try (MockBmc bmc = new MockBmc()) {
            StubDriverObject driverObject = new StubDriverObject(Map.of(
                    "host", "127.0.0.1",
                    "port", String.valueOf(bmc.port()),
                    "username", "admin",
                    "password", "secret",
                    "timeoutMs", "1000"
            ));
            IpmiDeviceDriver driver = new IpmiDeviceDriver();
            driver.initialize(driverObject);
            driver.connect();

            driver.readPoints(Map.of(
                    "power", "power",
                    "temperature", "CPU Temp"
            ));

            assertEquals("on", driverObject.variables.get("power").firstRow().get("value"));
            assertEquals(true, driverObject.variables.get("power").firstRow().get("powerOn"));
            assertEquals("40.0", driverObject.variables.get("temperature").firstRow().get("value"));
            assertEquals("CPU Temp", driverObject.variables.get("temperature").firstRow().get("sensor"));
            assertTrue(bmc.requestCount() > 0);
            driver.disconnect();
        }
    }

    @Test
    void sensorReadingAppliesFullSensorRecordFormula() throws Exception {
        // SDR type 01h (full sensor), linear y = M*x + B with M=10, B=0.
        IpmiSdrRecord record = IpmiSdrRecord.parse(fullSensorSdr(0x0A, "CPU Temp"));
        assertEquals(0x0A, record.sensorNumber());
        assertEquals("CPU Temp", record.name());

        assertEquals(40.0, record.convertReading(4), 1e-9);
    }

    @Test
    void sensorReadingReturnsPlainValueForCompactRecord() throws Exception {
        // SDR type 02h (compact sensor) carries no conversion formula — raw reading is used.
        IpmiSdrRecord record = IpmiSdrRecord.parse(compactSensorSdr(0x21, "Fan1"));
        assertEquals(0x21, record.sensorNumber());

        assertEquals(7.0, record.convertReading(7), 1e-9);
    }

    @Test
    void readWithoutConnectFails() {
        IpmiDeviceDriver driver = new IpmiDeviceDriver();
        driver.initialize(new StubDriverObject(Map.of()));

        DriverException error = assertThrows(DriverException.class, () ->
                driver.readPoints(Map.of("status", "power")));
        assertEquals("Not connected", error.getMessage());
    }

    @Test
    void writeIsReadOnly() {
        IpmiDeviceDriver driver = new IpmiDeviceDriver();
        driver.initialize(new StubDriverObject(Map.of()));

        DriverException error = assertThrows(DriverException.class, () ->
                driver.writePoint("status", DataRecord.single(
                        DataSchema.builder("ipmiValue")
                                .field("value", FieldType.STRING)
                                .field("reachable", FieldType.BOOLEAN)
                                .field("powerOn", FieldType.BOOLEAN)
                                .field("sensor", FieldType.STRING)
                                .build(),
                        Map.of("value", "on", "reachable", true, "powerOn", true, "sensor", "")
                )));
        assertTrue(error.getMessage().contains("read-only"));
    }

    private StubDriverObject driverConfig(int port, int timeoutMs) {
        return new StubDriverObject(Map.of(
                "host", "127.0.0.1",
                "port", String.valueOf(port),
                "timeoutMs", String.valueOf(timeoutMs)
        ));
    }

    /**
     * Full Sensor Record (SDR type 01h):
     * M=10, B=0, rExp=0, bExp=0, linear, unsigned readings, i.e. value = 10 * raw.
     */
    private static byte[] fullSensorSdr(int sensorNumber, String name) {
        byte[] nameBytes = name.getBytes(StandardCharsets.ISO_8859_1);
        byte[] sdr = new byte[48 + nameBytes.length];
        sdr[0] = 0x01;                                  // record id (LSB)
        sdr[1] = 0x00;                                  // record id (MSB)
        sdr[2] = 0x51;                                  // SDR version 1.5
        sdr[3] = 0x01;                                  // record type: full sensor record
        sdr[4] = (byte) (43 + nameBytes.length);        // record length
        sdr[5] = 0x20;                                  // sensor owner id
        sdr[7] = (byte) sensorNumber;
        sdr[8] = 0x01;                                  // entity id
        sdr[9] = 0x60;                                  // entity instance
        sdr[12] = 0x01;                                 // sensor type: temperature
        sdr[13] = 0x01;                                 // event/reading type: threshold
        sdr[20] = 0x00;                                 // sensor units 1: unsigned analog readings
        sdr[21] = 0x01;                                 // base unit: degrees C
        sdr[23] = 0x00;                                 // linearization: linear
        sdr[24] = 0x0A;                                 // M = 10
        sdr[47] = (byte) (0xC0 | nameBytes.length);     // ID string: 8-bit ASCII + length
        System.arraycopy(nameBytes, 0, sdr, 48, nameBytes.length);
        return sdr;
    }

    /** Compact Sensor Record (SDR type 02h): no M/B formula fields. */
    private static byte[] compactSensorSdr(int sensorNumber, String name) {
        byte[] nameBytes = name.getBytes(StandardCharsets.ISO_8859_1);
        byte[] sdr = new byte[32 + nameBytes.length];
        sdr[0] = 0x02;                                  // record id (LSB)
        sdr[2] = 0x51;                                  // SDR version 1.5
        sdr[3] = 0x02;                                  // record type: compact sensor record
        sdr[4] = (byte) (27 + nameBytes.length);        // record length
        sdr[5] = 0x20;                                  // sensor owner id
        sdr[7] = (byte) sensorNumber;
        sdr[8] = 0x01;                                  // entity id
        sdr[9] = 0x60;                                  // entity instance
        sdr[12] = 0x03;                                 // sensor type: fan
        sdr[13] = 0x01;                                 // event/reading type: threshold
        sdr[31] = (byte) (0xC0 | nameBytes.length);     // ID string: 8-bit ASCII + length
        System.arraycopy(nameBytes, 0, sdr, 32, nameBytes.length);
        return sdr;
    }

    /**
     * In-test UDP peer answering the RMCP Get Channel Authentication Capabilities request
     * with a same-framing response (class 0x07 first byte, completion code 0) — the exact
     * shape {@link RmcpPingClient} parses.
     */
    private static final class RmcpPeer implements AutoCloseable {

        private final DatagramSocket socket;
        private final Thread thread;
        private volatile boolean running = true;
        private volatile int requestCount;

        RmcpPeer() throws Exception {
            socket = new DatagramSocket(0);
            thread = new Thread(this::serve, "rmcp-peer");
            thread.setDaemon(true);
            thread.start();
        }

        int port() {
            return socket.getLocalPort();
        }

        int requestCount() {
            return requestCount;
        }

        void stopResponding() {
            running = false;
        }

        private void serve() {
            byte[] buffer = new byte[256];
            while (!socket.isClosed()) {
                try {
                    DatagramPacket request = new DatagramPacket(buffer, buffer.length);
                    socket.receive(request);
                    if (!running) {
                        continue;
                    }
                    requestCount++;
                    byte[] response = authCapabilitiesResponse(buffer, request.getLength());
                    socket.send(new DatagramPacket(response, response.length,
                            request.getAddress(), request.getPort()));
                } catch (Exception e) {
                    if (!socket.isClosed()) {
                        throw new IllegalStateException("RMCP peer failed", e);
                    }
                }
            }
        }

        private static byte[] authCapabilitiesResponse(byte[] request, int length) {
            byte seq = length > 2 ? request[2] : 0;
            return new byte[] {
                    0x07,                           // RMCP class: IPMI (what the client checks)
                    0x00,                           // no ACK
                    seq,                            // echoed sequence
                    0x00,
                    0x07, 0x00, 0x00, 0x00,         // IPMI session header (mirrors request framing)
                    0x00, 0x00, 0x00, 0x00,
                    0x00, (byte) 0x81, 0x00, 0x00,
                    0x00, 0x08,
                    0x38,                           // command: Get Channel Authentication Capabilities
                    0x00,                           // completion code: OK
                    0x0E,                           // channel number
                    0x16,                           // auth types: MD5 + straight password
                    0x00, 0x00, 0x00
            };
        }

        @Override
        public void close() {
            running = false;
            socket.close();
            try {
                thread.join(2000);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
    }

    private static final class MockBmc implements AutoCloseable {

        private final DatagramSocket socket;
        private final Thread thread;
        private final byte[] cpuTempSdr = fullSensorSdr(0x0A, "CPU Temp");
        private volatile boolean running = true;
        private volatile int requestCount;

        MockBmc() throws Exception {
            socket = new DatagramSocket(0);
            thread = new Thread(this::serve, "mock-bmc");
            thread.setDaemon(true);
            thread.start();
        }

        int port() {
            return socket.getLocalPort();
        }

        int requestCount() {
            return requestCount;
        }

        private void serve() {
            byte[] buffer = new byte[2048];
            while (!socket.isClosed()) {
                try {
                    DatagramPacket request = new DatagramPacket(buffer, buffer.length);
                    socket.receive(request);
                    requestCount++;
                    byte[] response = response(buffer, request.getLength());
                    socket.send(new DatagramPacket(response, response.length, request.getAddress(), request.getPort()));
                } catch (Exception ex) {
                    if (!socket.isClosed()) {
                        throw new IllegalStateException("Mock BMC failed", ex);
                    }
                }
            }
        }

        private byte[] response(byte[] request, int length) {
            if (length > 18 && Byte.toUnsignedInt(request[18]) == 0x38) {
                return RmcpPeer.authCapabilitiesResponse(request, length);
            }
            int seq = Byte.toUnsignedInt(request[1]);
            int command = Byte.toUnsignedInt(request[2]);
            byte[] payload = switch (command) {
                case IpmiLanClient.CMD_OPEN_SESSION -> new byte[] {1};
                case IpmiLanClient.CMD_GET_CHASSIS_STATUS -> new byte[] {1};
                case IpmiLanClient.CMD_RESERVE_SDR -> ByteBuffer.allocate(2)
                        .order(ByteOrder.LITTLE_ENDIAN)
                        .putShort((short) 0x1234)
                        .array();
                case IpmiLanClient.CMD_GET_SDR -> sdrResponse();
                case IpmiLanClient.CMD_GET_SENSOR_READING -> new byte[] {4};
                default -> new byte[0];
            };
            int completion = switch (command) {
                case IpmiLanClient.CMD_OPEN_SESSION,
                        IpmiLanClient.CMD_GET_CHASSIS_STATUS,
                        IpmiLanClient.CMD_RESERVE_SDR,
                        IpmiLanClient.CMD_GET_SDR,
                        IpmiLanClient.CMD_GET_SENSOR_READING -> 0;
                default -> 0xC1;
            };
            return IpmiLanClient.response(seq, command, completion, payload);
        }

        private byte[] sdrResponse() {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            ByteBuffer header = ByteBuffer.allocate(4).order(ByteOrder.LITTLE_ENDIAN);
            header.putShort((short) IpmiLanClient.SDR_END_RECORD_ID);
            header.putShort((short) cpuTempSdr.length);
            out.writeBytes(header.array());
            out.writeBytes(cpuTempSdr);
            return out.toByteArray();
        }

        @Override
        public void close() {
            running = false;
            socket.close();
            try {
                thread.join(2000);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
    }

    private static final class StubDriverObject implements DeviceDriver.DriverObject {

        private final Map<String, String> configuration;
        private final Map<String, DataRecord> variables = new HashMap<>();

        StubDriverObject(Map<String, String> configuration) {
            this.configuration = configuration;
        }

        @Override
        public PlatformObject deviceObject() {
            return new PlatformObject(
                    "test-ipmi",
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
        public Optional<DataRecord> getVariable(String name) {
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
