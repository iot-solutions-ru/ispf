package com.ispf.driver.messagestream;

import com.ispf.core.model.DataRecord;
import com.ispf.core.object.ObjectType;
import com.ispf.core.object.PlatformObject;
import com.ispf.driver.DeviceDriver;
import com.ispf.driver.DriverException;
import org.junit.jupiter.api.Test;

import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MessageStreamDeviceDriverTest {

    @Test
    void udpListenReceivesDatagram() throws Exception {
        int port = freeUdpPort();
        StubDriverObject driverObject = new StubDriverObject(Map.of(
                "protocol", "UDP",
                "listen", "true",
                "port", String.valueOf(port)
        ));
        MessageStreamDeviceDriver driver = new MessageStreamDeviceDriver();
        driver.initialize(driverObject);
        driver.connect();
        try {
            byte[] payload = "hello udp".getBytes(StandardCharsets.UTF_8);
            try (DatagramSocket sender = new DatagramSocket()) {
                sender.send(new DatagramPacket(payload, payload.length,
                        InetAddress.getByName("127.0.0.1"), port));
            }

            driver.readPoints(Map.of("feed", "stream"));

            DataRecord record = driverObject.variables.get("feed");
            assertEquals("hello udp", record.firstRow().get("stream"));
            assertEquals(payload.length, record.firstRow().get("bytesRead"));
        } finally {
            driver.disconnect();
        }
    }

    @Test
    void udpListenReturnsEmptyRecordOnTimeout() throws Exception {
        int port = freeUdpPort();
        StubDriverObject driverObject = new StubDriverObject(Map.of(
                "protocol", "UDP",
                "listen", "true",
                "port", String.valueOf(port)
        ));
        MessageStreamDeviceDriver driver = new MessageStreamDeviceDriver();
        driver.initialize(driverObject);
        driver.connect();
        try {
            driver.readPoints(Map.of("feed", "stream"));

            DataRecord record = driverObject.variables.get("feed");
            assertEquals("", record.firstRow().get("stream"));
            assertEquals(0, record.firstRow().get("bytesRead"));
        } finally {
            driver.disconnect();
        }
    }

    @Test
    void udpClientModeConnectsAndReadsEmptyOnTimeout() throws Exception {
        int port = freeUdpPort();
        StubDriverObject driverObject = new StubDriverObject(Map.of(
                "protocol", "UDP",
                "host", "127.0.0.1",
                "port", String.valueOf(port)
        ));
        MessageStreamDeviceDriver driver = new MessageStreamDeviceDriver();
        driver.initialize(driverObject);
        driver.connect();
        try {
            assertTrue(driver.isConnected());
            driver.readPoints(Map.of("feed", "stream"));

            DataRecord record = driverObject.variables.get("feed");
            assertEquals("", record.firstRow().get("stream"));
            assertEquals(0, record.firstRow().get("bytesRead"));
        } finally {
            driver.disconnect();
        }
    }

    @Test
    void tcpClientReadsLineFromServer() throws Exception {
        try (ServerSocket serverSocket = new ServerSocket(0, 1, InetAddress.getByName("127.0.0.1"))) {
            Thread server = acceptOnce(serverSocket, "hello tcp\n");
            StubDriverObject driverObject = new StubDriverObject(Map.of(
                    "protocol", "TCP",
                    "host", "127.0.0.1",
                    "port", String.valueOf(serverSocket.getLocalPort())
            ));
            MessageStreamDeviceDriver driver = new MessageStreamDeviceDriver();
            driver.initialize(driverObject);
            driver.connect();
            try {
                driver.readPoints(Map.of("feed", "stream"));

                DataRecord record = driverObject.variables.get("feed");
                assertEquals("hello tcp\n", record.firstRow().get("stream"));
                assertEquals(10, record.firstRow().get("bytesRead"));
            } finally {
                driver.disconnect();
                server.join(5000);
            }
        }
    }

    @Test
    void tcpClientReturnsEmptyRecordOnTimeout() throws Exception {
        try (ServerSocket serverSocket = new ServerSocket(0, 1, InetAddress.getByName("127.0.0.1"))) {
            Thread server = acceptOnce(serverSocket, null);
            StubDriverObject driverObject = new StubDriverObject(Map.of(
                    "protocol", "TCP",
                    "host", "127.0.0.1",
                    "port", String.valueOf(serverSocket.getLocalPort())
            ));
            MessageStreamDeviceDriver driver = new MessageStreamDeviceDriver();
            driver.initialize(driverObject);
            driver.connect();
            try {
                driver.readPoints(Map.of("feed", "stream"));

                DataRecord record = driverObject.variables.get("feed");
                assertEquals("", record.firstRow().get("stream"));
                assertEquals(0, record.firstRow().get("bytesRead"));
            } finally {
                driver.disconnect();
                server.join(5000);
            }
        }
    }

    @Test
    void tcpListenModeIsRejected() {
        StubDriverObject driverObject = new StubDriverObject(Map.of(
                "protocol", "TCP",
                "listen", "true",
                "port", "5000"
        ));
        MessageStreamDeviceDriver driver = new MessageStreamDeviceDriver();
        driver.initialize(driverObject);
        DriverException error = assertThrows(DriverException.class, driver::connect);
        assertTrue(error.getMessage().contains("TCP listen mode is not supported"));
    }

    @Test
    void readPointsRequiresConnection() {
        MessageStreamDeviceDriver driver = new MessageStreamDeviceDriver();
        driver.initialize(new StubDriverObject(Map.of()));
        assertThrows(DriverException.class, () -> driver.readPoints(Map.of("feed", "stream")));
    }

    @Test
    void writePointIsReadOnly() throws Exception {
        int port = freeUdpPort();
        StubDriverObject driverObject = new StubDriverObject(Map.of(
                "protocol", "UDP",
                "listen", "true",
                "port", String.valueOf(port)
        ));
        MessageStreamDeviceDriver driver = new MessageStreamDeviceDriver();
        driver.initialize(driverObject);
        driver.connect();
        try {
            assertThrows(DriverException.class, () -> driver.writePoint("feed", null));
        } finally {
            driver.disconnect();
        }
    }

    private static int freeUdpPort() throws Exception {
        try (DatagramSocket socket = new DatagramSocket(0)) {
            return socket.getLocalPort();
        }
    }

    private static Thread acceptOnce(ServerSocket serverSocket, String line) {
        Thread thread = new Thread(() -> {
            try (Socket client = serverSocket.accept()) {
                if (line != null) {
                    client.getOutputStream().write(line.getBytes(StandardCharsets.UTF_8));
                    client.getOutputStream().flush();
                }
                Thread.sleep(2500);
            } catch (Exception ignored) {
                // best effort test server
            }
        }, "message-stream-test-server");
        thread.setDaemon(true);
        thread.start();
        return thread;
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
                    "message-stream-test",
                    "root.platform.devices.test.messagestream",
                    ObjectType.DEVICE,
                    "MessageStream",
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
