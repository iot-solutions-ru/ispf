package com.ispf.driver.dhcp;

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
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DhcpDeviceDriverTest {

    private static final byte[] SERVER_ID = {(byte) 192, (byte) 168, 1, 1};
    private static final byte[] OFFERED_IP = {10, 0, 0, 42};
    private static final long LEASE_SECONDS = 3600;

    @Test
    void discoversServerOverLoopback() throws Exception {
        int listenPort = freeUdpPort();
        AtomicReference<DatagramPacket> received = new AtomicReference<>();
        try (DatagramSocket serverSocket = new DatagramSocket(0, InetAddress.getByName("127.0.0.1"))) {
            serverSocket.setSoTimeout(5000);
            Thread server = respondWithOffer(serverSocket, listenPort, received);
            StubDriverObject driverObject = new StubDriverObject(Map.of(
                    "bindAddress", "127.0.0.1",
                    "timeoutMs", "3000",
                    "serverPort", String.valueOf(serverSocket.getLocalPort()),
                    "listenPort", String.valueOf(listenPort),
                    "broadcastAddress", "127.0.0.1"
            ));
            DhcpDeviceDriver driver = new DhcpDeviceDriver();
            driver.initialize(driverObject);
            driver.connect();
            try {
                driver.readPoints(Map.of(
                        "server", "serverIp",
                        "lease", "lease"
                ));

                DataRecord serverRecord = driverObject.variables.get("server");
                assertEquals("192.168.1.1", serverRecord.firstRow().get("value"));
                assertEquals(true, serverRecord.firstRow().get("leased"));
                assertEquals(LEASE_SECONDS, serverRecord.firstRow().get("leaseSeconds"));

                DataRecord lease = driverObject.variables.get("lease");
                assertEquals("obtained", lease.firstRow().get("value"));
                assertEquals(true, lease.firstRow().get("leased"));
                assertEquals(LEASE_SECONDS, lease.firstRow().get("leaseSeconds"));
            } finally {
                driver.disconnect();
                server.join(5000);
            }

            DatagramPacket discover = received.get();
            assertNotNull(discover);
            assertEquals(1, discover.getData()[0] & 0xFF, "expected BOOTREQUEST op");
        }
    }

    @Test
    void readPointsRequiresConnection() {
        DhcpDeviceDriver driver = new DhcpDeviceDriver();
        driver.initialize(new StubDriverObject(Map.of()));
        assertThrows(DriverException.class, () -> driver.readPoints(Map.of("server", "serverIp")));
    }

    @Test
    void writePointIsReadOnly() {
        DhcpDeviceDriver driver = new DhcpDeviceDriver();
        driver.initialize(new StubDriverObject(Map.of()));
        assertThrows(DriverException.class, () -> driver.writePoint("server", null));
    }

    private static int freeUdpPort() throws Exception {
        try (DatagramSocket socket = new DatagramSocket(0)) {
            return socket.getLocalPort();
        }
    }

    private static Thread respondWithOffer(
            DatagramSocket serverSocket,
            int clientPort,
            AtomicReference<DatagramPacket> received
    ) {
        Thread thread = new Thread(() -> {
            try {
                byte[] buffer = new byte[576];
                DatagramPacket discover = new DatagramPacket(buffer, buffer.length);
                serverSocket.receive(discover);
                received.set(discover);
                byte[] xid = new byte[4];
                System.arraycopy(discover.getData(), 4, xid, 0, 4);
                byte[] offer = buildOfferPacket(xid);
                DatagramPacket response = new DatagramPacket(
                        offer, offer.length, InetAddress.getByName("127.0.0.1"), clientPort);
                serverSocket.send(response);
            } catch (Exception ignored) {
                // best effort test server
            }
        }, "dhcp-test-server");
        thread.setDaemon(true);
        thread.start();
        return thread;
    }

    private static byte[] buildOfferPacket(byte[] xid) {
        byte[] packet = new byte[300];
        packet[0] = 2; // BOOTREPLY
        packet[1] = 1; // Ethernet
        packet[2] = 6;
        packet[3] = 0;
        System.arraycopy(xid, 0, packet, 4, 4);
        System.arraycopy(OFFERED_IP, 0, packet, 16, 4);
        packet[236] = 0x63;
        packet[237] = (byte) 0x82;
        packet[238] = 0x53;
        packet[239] = 0x63;
        int index = 240;
        packet[index++] = 53; // message type
        packet[index++] = 1;
        packet[index++] = 2; // OFFER
        packet[index++] = 54; // server identifier
        packet[index++] = 4;
        System.arraycopy(SERVER_ID, 0, packet, index, 4);
        index += 4;
        packet[index++] = 51; // lease time
        packet[index++] = 4;
        packet[index++] = (byte) (LEASE_SECONDS >>> 24);
        packet[index++] = (byte) (LEASE_SECONDS >>> 16);
        packet[index++] = (byte) (LEASE_SECONDS >>> 8);
        packet[index++] = (byte) LEASE_SECONDS;
        packet[index] = (byte) 255;
        return packet;
    }

    private static final class StubDriverObject implements DeviceDriver.DriverObject {
        private final Map<String, String> configuration;
        private final Map<String, DataRecord> variables = new HashMap<>();
        private final Map<String, Instant> observedAt = new HashMap<>();

        StubDriverObject(Map<String, String> configuration) {
            this.configuration = configuration;
        }

        @Override
        public PlatformObject deviceObject() {
            return new PlatformObject(
                    "dhcp-test",
                    "root.platform.devices.test.dhcp",
                    ObjectType.DEVICE,
                    "Dhcp",
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
            return configuration;
        }
    }
}
