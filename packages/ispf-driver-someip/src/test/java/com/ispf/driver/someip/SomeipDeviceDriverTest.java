package com.ispf.driver.someip;

import com.ispf.core.model.DataRecord;
import com.ispf.core.model.DataSchema;
import com.ispf.core.model.FieldType;
import com.ispf.core.object.ObjectType;
import com.ispf.core.object.PlatformObject;
import com.ispf.driver.DeviceDriver;
import com.ispf.driver.DriverException;
import com.ispf.driver.DriverMaturity;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Loopback tests for {@link SomeipDeviceDriver} against an in-process SOME/IP-lab server.
 * Certifies the header+payload lab subset only — not full Service Discovery / secure AUTOSAR.
 */
class SomeipDeviceDriverTest {

    private SomeipDeviceDriver driver;
    private FakeSomeipServer server;

    @AfterEach
    void tearDown() throws Exception {
        if (driver != null) {
            driver.disconnect();
            driver = null;
        }
        if (server != null) {
            server.close();
            server = null;
        }
    }

    @Test
    void metadataIsProductionReadWriteSomeipLab() {
        driver = new SomeipDeviceDriver();
        assertEquals("someip", driver.metadata().id());
        assertEquals(DriverMaturity.PRODUCTION, driver.metadata().maturity());
        assertEquals(Set.of("read", "write"), driver.metadata().capabilities());
        assertTrue(driver.metadata().description().toLowerCase(Locale.ROOT).contains("some/ip"));
        assertTrue(driver.metadata().description().toLowerCase(Locale.ROOT).contains("not"));
    }

    @Test
    void udpReadServiceMethod() throws Exception {
        server = FakeSomeipServer.udp();
        server.put(0x1234, 0x0001, "hello-lab".getBytes(StandardCharsets.US_ASCII));
        server.start();

        StubDriverObject object = new StubDriverObject(Map.of(
                "host", "127.0.0.1",
                "port", String.valueOf(server.port()),
                "transport", "udp",
                "timeoutMs", "2000"
        ));
        driver = new SomeipDeviceDriver();
        driver.initialize(object);
        driver.connect();
        assertTrue(driver.isConnected());

        driver.readPoints(Map.of("svc", "0x1234:0x0001"));
        assertEquals("hello-lab", object.variables.get("svc").firstRow().get("value"));
        assertEquals("0x1234", object.variables.get("svc").firstRow().get("service"));
        assertEquals("0x0001", object.variables.get("svc").firstRow().get("method"));
        assertEquals(0x1234, SomeipDeviceDriver.parseServiceMethod("0x1234:0x0001").service());
    }

    @Test
    void udpFireForgetWriteThenRead() throws Exception {
        server = FakeSomeipServer.udp();
        server.put(0x1234, 0x0001, "old".getBytes(StandardCharsets.US_ASCII));
        server.start();

        StubDriverObject object = new StubDriverObject(Map.of(
                "host", "127.0.0.1",
                "port", String.valueOf(server.port()),
                "transport", "udp",
                "writeMode", "fireForget",
                "timeoutMs", "2000"
        ));
        driver = new SomeipDeviceDriver();
        driver.initialize(object);
        driver.connect();

        driver.readPoints(Map.of("svc", "0x1234:0x0001"));
        driver.writePoint("svc", DataRecord.single(
                DataSchema.builder("v").field("value", FieldType.STRING).build(),
                Map.of("value", "new-value")
        ));
        assertTrue(server.awaitWrite(2000));
        assertEquals("new-value", new String(server.get(0x1234, 0x0001), StandardCharsets.US_ASCII));

        driver.readPoints(Map.of("svc", "0x1234:0x0001"));
        assertEquals("new-value", object.variables.get("svc").firstRow().get("value"));
    }

    @Test
    void tcpRequestResponseWrite() throws Exception {
        server = FakeSomeipServer.tcp();
        server.put(0xABCD, 0x0010, "seed".getBytes(StandardCharsets.US_ASCII));
        server.start();

        StubDriverObject object = new StubDriverObject(Map.of(
                "host", "127.0.0.1",
                "port", String.valueOf(server.port()),
                "transport", "tcp",
                "writeMode", "requestResponse",
                "timeoutMs", "2000"
        ));
        driver = new SomeipDeviceDriver();
        driver.initialize(object);
        driver.connect();

        driver.readPoints(Map.of("m", "0xABCD:0x0010"));
        driver.writePoint("m", DataRecord.single(
                DataSchema.builder("v").field("data", FieldType.STRING).build(),
                Map.of("data", "CAFE")
        ));
        assertEquals("CAFE", SomeipDeviceDriver.toHex(server.get(0xABCD, 0x0010)));
        assertEquals("CAFE", object.variables.get("m").firstRow().get("data"));
    }

    @Test
    void readPointsBeforeConnectThrows() {
        driver = new SomeipDeviceDriver();
        driver.initialize(new StubDriverObject(Map.of()));
        DriverException error = assertThrows(DriverException.class, () ->
                driver.readPoints(Map.of("x", "0x1234:0x0001")));
        assertTrue(error.getMessage().contains("Not connected"));
    }

    private static final class FakeSomeipServer implements AutoCloseable {

        private final boolean tcp;
        private final DatagramSocket udpSocket;
        private final ServerSocket tcpServer;
        private final ExecutorService executor = Executors.newCachedThreadPool(runnable -> {
            Thread thread = new Thread(runnable, "fake-someip");
            thread.setDaemon(true);
            return thread;
        });
        private final Map<String, byte[]> values = new ConcurrentHashMap<>();
        private final AtomicReference<byte[]> lastWrite = new AtomicReference<>();
        private final CountDownLatch ready = new CountDownLatch(1);

        private FakeSomeipServer(boolean tcp) throws IOException {
            this.tcp = tcp;
            if (tcp) {
                tcpServer = new ServerSocket();
                tcpServer.bind(new InetSocketAddress("127.0.0.1", 0));
                udpSocket = null;
            } else {
                udpSocket = new DatagramSocket(new InetSocketAddress("127.0.0.1", 0));
                tcpServer = null;
            }
        }

        static FakeSomeipServer udp() throws IOException {
            return new FakeSomeipServer(false);
        }

        static FakeSomeipServer tcp() throws IOException {
            return new FakeSomeipServer(true);
        }

        int port() {
            return tcp ? tcpServer.getLocalPort() : udpSocket.getLocalPort();
        }

        void put(int service, int method, byte[] data) {
            values.put(key(service, method), data.clone());
        }

        byte[] get(int service, int method) {
            return values.get(key(service, method));
        }

        boolean awaitWrite(long timeoutMs) throws InterruptedException {
            long deadline = System.currentTimeMillis() + timeoutMs;
            while (System.currentTimeMillis() < deadline) {
                if (lastWrite.get() != null) {
                    return true;
                }
                Thread.sleep(20);
            }
            return lastWrite.get() != null;
        }

        void start() throws InterruptedException {
            if (tcp) {
                executor.submit(() -> { ready.countDown(); tcpAcceptLoop(); });
            } else {
                executor.submit(() -> { ready.countDown(); udpLoop(); });
            }
            if (!ready.await(2, TimeUnit.SECONDS)) {
                throw new IllegalStateException("fake SOME/IP server failed to start");
            }
            Thread.sleep(20);
        }

        private void tcpAcceptLoop() {
            while (!tcpServer.isClosed()) {
                try {
                    Socket socket = tcpServer.accept();
                    executor.submit(() -> handleTcp(socket));
                } catch (IOException e) {
                    if (tcpServer.isClosed()) {
                        return;
                    }
                }
            }
        }

        private void handleTcp(Socket socket) {
            try (socket) {
                while (true) {
                    byte[] frame = SomeipDeviceDriver.readTcpFrame(socket.getInputStream());
                    byte[] response = handleFrame(frame);
                    if (response != null) {
                        socket.getOutputStream().write(response);
                        socket.getOutputStream().flush();
                    }
                }
            } catch (IOException ignored) {
                // client closed
            }
        }

        private void udpLoop() {
            byte[] buf = new byte[65535];
            while (!udpSocket.isClosed()) {
                try {
                    DatagramPacket packet = new DatagramPacket(buf, buf.length);
                    udpSocket.receive(packet);
                    byte[] frame = Arrays.copyOf(packet.getData(), packet.getLength());
                    byte[] response = handleFrame(frame);
                    if (response != null) {
                        DatagramPacket reply = new DatagramPacket(
                                response, response.length, packet.getSocketAddress());
                        udpSocket.send(reply);
                    }
                } catch (IOException e) {
                    if (udpSocket.isClosed()) {
                        return;
                    }
                }
            }
        }

        private byte[] handleFrame(byte[] frame) {
            SomeipDeviceDriver.SomeipFrame parsed = SomeipDeviceDriver.decodeFrame(frame);
            String mapKey = key(parsed.service(), parsed.method());
            if (parsed.messageType() == SomeipDeviceDriver.MSG_REQUEST_NO_RETURN) {
                values.put(mapKey, parsed.payload().clone());
                lastWrite.set(parsed.payload().clone());
                return null;
            }
            if (parsed.messageType() == SomeipDeviceDriver.MSG_REQUEST) {
                if (parsed.payload().length > 0) {
                    values.put(mapKey, parsed.payload().clone());
                    lastWrite.set(parsed.payload().clone());
                }
                byte[] payload = values.getOrDefault(mapKey, new byte[0]);
                return SomeipDeviceDriver.encodeFrame(
                        parsed.service(),
                        parsed.method(),
                        parsed.clientId(),
                        parsed.sessionId(),
                        SomeipDeviceDriver.MSG_RESPONSE,
                        SomeipDeviceDriver.E_OK,
                        payload
                );
            }
            return null;
        }

        private static String key(int service, int method) {
            return service + ":" + method;
        }

        @Override
        public void close() throws Exception {
            if (udpSocket != null) {
                udpSocket.close();
            }
            if (tcpServer != null) {
                tcpServer.close();
            }
            executor.shutdownNow();
            executor.awaitTermination(2, TimeUnit.SECONDS);
        }
    }

    private static final class StubDriverObject implements DeviceDriver.DriverObject {

        private final Map<String, String> configuration;
        private final Map<String, DataRecord> variables = new ConcurrentHashMap<>();

        StubDriverObject(Map<String, String> configuration) {
            this.configuration = configuration;
        }

        @Override
        public PlatformObject deviceObject() {
            return new PlatformObject(
                    "test-someip",
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
