package com.ispf.driver.someip;

import com.ispf.core.model.DataRecord;
import com.ispf.core.model.DataSchema;
import com.ispf.core.model.FieldType;
import com.ispf.core.object.ObjectType;
import com.ispf.core.object.PlatformObject;
import com.ispf.driver.DeviceDriver;
import com.ispf.driver.DriverException;
import com.ispf.driver.DriverMaturity;
import com.ispf.driver.someip.codec.SomeipCodec;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * ServerSocket peer tests for AUTOSAR SOME/IP over TCP.
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
    void emptyRequestHeaderMatchesLiteralAutosarFrame() {
        byte[] frame = SomeipCodec.encodeFrame(
                0x0100, 0x0001, 0x0001, 0x0001,
                SomeipCodec.MSG_REQUEST, SomeipCodec.E_OK, new byte[0]);
        assertArrayEquals(new byte[]{
                0x01, 0x00, 0x00, 0x01,
                0x00, 0x00, 0x00, 0x08,
                0x00, 0x01, 0x00, 0x01,
                0x01, 0x01, 0x00, 0x00
        }, frame);
    }

    @Test
    void metadataIsProductionReadWriteSomeip() {
        driver = new SomeipDeviceDriver();
        assertEquals("someip", driver.metadata().id());
        assertEquals(DriverMaturity.PRODUCTION, driver.metadata().maturity());
        assertEquals(Set.of("read", "write"), driver.metadata().capabilities());
        String description = driver.metadata().description().toLowerCase(Locale.ROOT);
        assertTrue(description.contains("some/ip"));
        assertTrue(!description.contains("lab"));
        assertTrue(!description.contains("stub") && !description.contains("placeholder"));
    }

    @Test
    void tcpReadServiceMethod() throws Exception {
        server = new FakeSomeipServer();
        server.put(0x0100, 0x0001, "hello".getBytes(StandardCharsets.US_ASCII));
        server.start();

        StubDriverObject object = new StubDriverObject(Map.of(
                "host", "127.0.0.1",
                "port", String.valueOf(server.port()),
                "clientId", "0x0001",
                "timeoutMs", "2000"
        ));
        driver = new SomeipDeviceDriver();
        driver.initialize(object);
        driver.connect();
        assertTrue(driver.isConnected());

        driver.readPoints(Map.of("svc", "0x0100:0x0001"));
        assertEquals("hello", object.variables.get("svc").firstRow().get("value"));
        assertEquals("0x0100", object.variables.get("svc").firstRow().get("service"));
        assertEquals("0x0001", object.variables.get("svc").firstRow().get("method"));
        assertEquals(0x0100, SomeipPoint.parse("0x0100:0x0001").service());
    }

    @Test
    void tcpFireForgetWriteThenRead() throws Exception {
        server = new FakeSomeipServer();
        server.put(0x1234, 0x0001, "old".getBytes(StandardCharsets.US_ASCII));
        server.start();

        StubDriverObject object = new StubDriverObject(Map.of(
                "host", "127.0.0.1",
                "port", String.valueOf(server.port()),
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
        server = new FakeSomeipServer();
        server.put(0xABCD, 0x0010, "seed".getBytes(StandardCharsets.US_ASCII));
        server.start();

        StubDriverObject object = new StubDriverObject(Map.of(
                "host", "127.0.0.1",
                "port", String.valueOf(server.port()),
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
        assertEquals("CAFE", SomeipCodec.toHex(server.get(0xABCD, 0x0010)));
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

        private final ServerSocket tcpServer;
        private final ExecutorService executor = Executors.newCachedThreadPool(runnable -> {
            Thread thread = new Thread(runnable, "fake-someip");
            thread.setDaemon(true);
            return thread;
        });
        private final Map<String, byte[]> values = new ConcurrentHashMap<>();
        private final AtomicReference<byte[]> lastWrite = new AtomicReference<>();
        private final CountDownLatch ready = new CountDownLatch(1);

        FakeSomeipServer() throws IOException {
            tcpServer = new ServerSocket();
            tcpServer.bind(new InetSocketAddress("127.0.0.1", 0));
        }

        int port() {
            return tcpServer.getLocalPort();
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
            var _ = executor.submit(() -> {
                ready.countDown();
                tcpAcceptLoop();
            });
            if (!ready.await(2, TimeUnit.SECONDS)) {
                throw new IllegalStateException("fake SOME/IP server failed to start");
            }
            Thread.sleep(20);
        }

        private void tcpAcceptLoop() {
            while (!tcpServer.isClosed()) {
                try {
                    Socket socket = tcpServer.accept();
                    var _ = executor.submit(() -> handleTcp(socket));
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
                    byte[] frame = SomeipCodec.readTcpFrame(socket.getInputStream());
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

        private byte[] handleFrame(byte[] frame) {
            SomeipCodec.SomeipFrame parsed = SomeipCodec.decodeFrame(frame);
            String mapKey = key(parsed.service(), parsed.method());
            if (parsed.messageType() == SomeipCodec.MSG_REQUEST_NO_RETURN) {
                byte[] payload = parsed.payload();
                values.put(mapKey, payload.clone());
                lastWrite.set(payload.clone());
                return null;
            }
            if (parsed.messageType() == SomeipCodec.MSG_REQUEST) {
                byte[] requestPayload = parsed.payload();
                if (requestPayload.length > 0) {
                    values.put(mapKey, requestPayload.clone());
                    lastWrite.set(requestPayload.clone());
                }
                byte[] payload = values.getOrDefault(mapKey, new byte[0]);
                return SomeipCodec.encodeFrame(
                        parsed.service(),
                        parsed.method(),
                        parsed.clientId(),
                        parsed.sessionId(),
                        SomeipCodec.MSG_RESPONSE,
                        SomeipCodec.E_OK,
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
            tcpServer.close();
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
