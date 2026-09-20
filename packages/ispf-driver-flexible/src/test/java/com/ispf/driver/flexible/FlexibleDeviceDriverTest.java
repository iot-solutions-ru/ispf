package com.ispf.driver.flexible;

import com.ispf.core.model.DataRecord;
import com.ispf.core.object.ObjectType;
import com.ispf.core.object.PlatformObject;
import com.ispf.driver.DeviceDriver;
import com.ispf.driver.DriverException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.function.UnaryOperator;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * End-to-end loopback: the driver talks to a scripted TCP / UDP device on 127.0.0.1 — legacy
 * {@code request:regex} points, delimiter-framed reads and the exchange pipeline with checksum.
 */
class FlexibleDeviceDriverTest {

    private ServerSocket tcpServer;
    private DatagramSocket udpServer;
    private final CopyOnWriteArrayList<String> requestsSeen = new CopyOnWriteArrayList<>();

    @AfterEach
    void stopPeers() throws IOException {
        if (tcpServer != null) {
            tcpServer.close();
        }
        if (udpServer != null) {
            udpServer.close();
        }
    }

    @Test
    void legacyTcpPointExtractsRegexGroupFromDeviceReply() throws Exception {
        int port = startTcpDevice(request -> switch (request.trim()) {
            case "STATUS" -> "OK=42\r\n";
            case "TEMP" -> "T=21.5C\r\n";
            default -> "ERR\r\n";
        });

        StubDriverObject driverObject = new StubDriverObject(Map.of(
                "protocol", "TCP", "host", "127.0.0.1", "port", String.valueOf(port), "timeoutMs", "2000"
        ));
        FlexibleDeviceDriver driver = new FlexibleDeviceDriver();
        driver.initialize(driverObject);
        driver.connect();
        assertTrue(driver.isConnected());
        driver.readPoints(Map.of(
                "status", "STATUS:OK=(\\d+)",
                "temp", "TEMP:T=([0-9.]+)C"
        ));

        assertEquals("42", driverObject.variables.get("status").firstRow().get("value"));
        assertEquals("21.5", driverObject.variables.get("temp").firstRow().get("value"));
        assertEquals("OK=42\r\n", driverObject.variables.get("status").firstRow().get("raw"));
        assertTrue(requestsSeen.contains("STATUS") && requestsSeen.contains("TEMP"), requestsSeen.toString());
        driver.disconnect();
    }

    @Test
    void delimiterReadModeStopsAtEtxEvenWhenDeviceKeepsTalking() throws Exception {
        int port = startTcpDevice(request -> "\u0002VAL=7\u0003TRAILING-NOISE");

        StubDriverObject driverObject = new StubDriverObject(Map.of(
                "protocol", "TCP", "host", "127.0.0.1", "port", String.valueOf(port), "timeoutMs", "2000",
                "readMode", "delimiter", "readUntilHex", "03"
        ));
        FlexibleDeviceDriver driver = new FlexibleDeviceDriver();
        driver.initialize(driverObject);
        driver.connect();
        driver.readPoints(Map.of("val", "READ:VAL=(\\d+)"));

        DataRecord record = driverObject.variables.get("val");
        assertEquals("7", record.firstRow().get("value"));
        String raw = (String) record.firstRow().get("raw");
        assertTrue(raw.endsWith("\u0003") || raw.endsWith("<ETX>") || !raw.contains("TRAILING"),
                "frame must stop at ETX: " + raw);
        driver.disconnect();
    }

    @Test
    void udpExchangeRoundTripsThroughDatagramDevice() throws Exception {
        int port = startUdpDevice(request -> "PONG " + request.trim());

        StubDriverObject driverObject = new StubDriverObject(Map.of(
                "protocol", "UDP", "host", "127.0.0.1", "port", String.valueOf(port), "timeoutMs", "2000"
        ));
        FlexibleDeviceDriver driver = new FlexibleDeviceDriver();
        driver.initialize(driverObject);
        driver.connect();
        driver.readPoints(Map.of("echo", "PING:PONG (\\w+)"));

        assertEquals("PING", driverObject.variables.get("echo").firstRow().get("value"));
        driver.disconnect();
    }

    @Test
    void unreachableDeviceSurfacesAsDriverException() throws Exception {
        int closedPort;
        try (ServerSocket probe = new ServerSocket(0, 1, InetAddress.getLoopbackAddress())) {
            closedPort = probe.getLocalPort();
        }
        StubDriverObject driverObject = new StubDriverObject(Map.of(
                "protocol", "TCP", "host", "127.0.0.1", "port", String.valueOf(closedPort), "timeoutMs", "500"
        ));
        FlexibleDeviceDriver driver = new FlexibleDeviceDriver();
        driver.initialize(driverObject);
        driver.connect();

        DriverException error = assertThrows(DriverException.class,
                () -> driver.readPoints(Map.of("status", "STATUS:OK=(\\d+)")));
        assertTrue(error.getMessage().contains("exchange failed"), error.getMessage());
        assertTrue(driverObject.variables.isEmpty(), "no value must be published on a failed exchange");
    }

    @Test
    void readOnlyDriverRejectsWritesAndRequiresConnection() {
        FlexibleDeviceDriver driver = new FlexibleDeviceDriver();
        driver.initialize(new StubDriverObject(Map.of("protocol", "TCP", "host", "127.0.0.1", "port", "1")));
        assertThrows(DriverException.class, () -> driver.readPoints(Map.of("s", "STATUS")));
        DriverException error = assertThrows(DriverException.class, () -> driver.writePoint("s", null));
        assertTrue(error.getMessage().contains("read-only"), error.getMessage());
    }

    /** Scripted TCP device: one request/response per connection, replies through {@code script}. */
    private int startTcpDevice(UnaryOperator<String> script) throws Exception {
        tcpServer = new ServerSocket(0, 5, InetAddress.getLoopbackAddress());
        CountDownLatch listening = new CountDownLatch(1);
        Thread thread = new Thread(() -> {
            listening.countDown();
            while (!tcpServer.isClosed()) {
                try (Socket client = tcpServer.accept()) {
                    client.setSoTimeout(2000);
                    String request = readRequest(client.getInputStream());
                    requestsSeen.add(request.trim());
                    OutputStream out = client.getOutputStream();
                    out.write(script.apply(request).getBytes(StandardCharsets.UTF_8));
                    out.flush();
                    Thread.sleep(50); // let idle-mode readers see the whole burst before close
                } catch (IOException | InterruptedException ignored) {
                    // server closed by the test or client went away
                }
            }
        }, "flexible-tcp-device");
        thread.setDaemon(true);
        thread.start();
        assertTrue(listening.await(2, TimeUnit.SECONDS));
        return tcpServer.getLocalPort();
    }

    private static String readRequest(InputStream in) throws IOException {
        byte[] buffer = new byte[256];
        int read = in.read(buffer);
        return read <= 0 ? "" : new String(buffer, 0, read, StandardCharsets.UTF_8);
    }

    /** Scripted UDP device: echoes {@code script(request)} back to the sender. */
    private int startUdpDevice(UnaryOperator<String> script) throws Exception {
        udpServer = new DatagramSocket(0, InetAddress.getLoopbackAddress());
        Thread thread = new Thread(() -> {
            byte[] buffer = new byte[512];
            while (!udpServer.isClosed()) {
                try {
                    DatagramPacket packet = new DatagramPacket(buffer, buffer.length);
                    udpServer.receive(packet);
                    String request = new String(packet.getData(), 0, packet.getLength(), StandardCharsets.UTF_8);
                    requestsSeen.add(request.trim());
                    byte[] reply = script.apply(request).getBytes(StandardCharsets.UTF_8);
                    udpServer.send(new DatagramPacket(reply, reply.length, packet.getSocketAddress()));
                } catch (IOException ignored) {
                    // closed by the test
                }
            }
        }, "flexible-udp-device");
        thread.setDaemon(true);
        thread.start();
        return udpServer.getLocalPort();
    }

    private static final class StubDriverObject implements DeviceDriver.DriverObject {
        private final Map<String, String> configuration;
        private final Map<String, DataRecord> variables = new HashMap<>();

        StubDriverObject(Map<String, String> configuration) {
            this.configuration = configuration;
        }

        @Override
        public PlatformObject deviceObject() {
            return new PlatformObject("test-flex", "root.platform.devices.flex", ObjectType.DEVICE, "Test", "", null);
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
