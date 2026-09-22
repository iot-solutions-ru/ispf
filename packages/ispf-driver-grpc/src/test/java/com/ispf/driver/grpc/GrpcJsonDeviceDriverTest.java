package com.ispf.driver.grpc;

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

import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
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

class GrpcJsonDeviceDriverTest {

    /**
     * Handwritten HTTP/2 preface + empty SETTINGS (do not build via encoder).
     * Preface: PRI * HTTP/2.0\r\n\r\nSM\r\n\r\n then SETTINGS length=0 type=4 flags=0 stream=0.
     */
    private static final byte[] EXPECTED_CONNECT = {
            0x50, 0x52, 0x49, 0x20, 0x2A, 0x20, 0x48, 0x54, 0x54, 0x50, 0x2F, 0x32, 0x2E, 0x30, 0x0D, 0x0A,
            0x0D, 0x0A, 0x53, 0x4D, 0x0D, 0x0A, 0x0D, 0x0A,
            0x00, 0x00, 0x00, 0x04, 0x00, 0x00, 0x00, 0x00, 0x00
    };

    private static final byte[] SETTINGS_ACK = {
            0x00, 0x00, 0x00, 0x04, 0x01, 0x00, 0x00, 0x00, 0x00
    };

    private GrpcJsonDeviceDriver driver;
    private FakeGrpcPeer peer;

    @AfterEach
    void tearDown() throws Exception {
        if (driver != null) {
            driver.disconnect();
            driver = null;
        }
        if (peer != null) {
            peer.close();
            peer = null;
        }
    }

    @Test
    void metadataIsBetaLabHttp2PrefaceNotHpackOrProtobufRpc() {
        driver = new GrpcJsonDeviceDriver();
        assertEquals("grpc", driver.metadata().id());
        assertEquals(DriverMaturity.BETA, driver.metadata().maturity());
        String description = driver.metadata().description().toLowerCase(Locale.ROOT);
        assertTrue(description.contains("lab"));
        assertTrue(description.contains("http/2") || description.contains("preface"));
        assertTrue(description.contains("not hpack") || description.contains("hpack"));
        assertTrue(description.contains("not") && description.contains("protobuf"));
    }

    @Test
    void connectWritesHttp2PrefaceAndSettingsExpectsAck() throws Exception {
        peer = new FakeGrpcPeer();
        peer.start();
        assertTrue(peer.awaitReady(2, TimeUnit.SECONDS));

        StubDriverObject object = config(peer.port());
        driver = new GrpcJsonDeviceDriver();
        driver.initialize(object);
        driver.connect();
        assertTrue(driver.isConnected());
        assertTrue(peer.awaitConnect(2, TimeUnit.SECONDS));
        assertArrayEquals(EXPECTED_CONNECT, peer.capturedConnect());
        assertEquals(33, peer.capturedConnect().length);
    }

    @Test
    void lengthPrefixedLabCallAfterPreface() throws Exception {
        peer = new FakeGrpcPeer();
        peer.start();
        assertTrue(peer.awaitReady(2, TimeUnit.SECONDS));

        StubDriverObject object = config(peer.port());
        driver = new GrpcJsonDeviceDriver();
        driver.initialize(object);
        driver.connect();
        driver.readPoints(Map.of("greeting", "helloworld.Greeter/SayHello#message"));
        DataRecord record = object.variables.get("greeting");
        assertEquals("Hello world", record.firstRow().get("value"));
        assertEquals("helloworld.Greeter/SayHello", record.firstRow().get("method"));

        driver.writePoint("greeting", DataRecord.single(
                DataSchema.builder("v").field("value", FieldType.STRING).build(),
                Map.of("value", "ISPF")
        ));
        assertEquals("Hello ISPF", object.variables.get("greeting").firstRow().get("value"));
        driver.disconnect();
    }

    @Test
    void pointParseRequiresServiceMethod() {
        assertThrows(IllegalArgumentException.class, () -> GrpcJsonPoint.parse("SayHello"));
        GrpcJsonPoint point = GrpcJsonPoint.parse("pkg.Svc/Method#field");
        assertEquals("/pkg.Svc/Method", point.httpPath());
        assertEquals("field", point.field());
    }

    @Test
    void readBeforeConnectThrows() {
        GrpcJsonDeviceDriver underTest = new GrpcJsonDeviceDriver();
        underTest.initialize(new StubDriverObject(Map.of()));
        DriverException error = assertThrows(DriverException.class, () ->
                underTest.readPoints(Map.of("g", "Greeter/SayHello#message")));
        assertTrue(error.getMessage().contains("Not connected"));
    }

    private StubDriverObject config(int port) {
        return new StubDriverObject(Map.of(
                "host", "127.0.0.1",
                "port", String.valueOf(port),
                "timeoutMs", "3000",
                "defaultName", "world"
        ));
    }

    private static final class FakeGrpcPeer implements AutoCloseable {

        private final ServerSocket serverSocket;
        private final ExecutorService executor = Executors.newCachedThreadPool(runnable -> {
            Thread thread = new Thread(runnable, "fake-grpc-peer");
            thread.setDaemon(true);
            return thread;
        });
        private final CountDownLatch ready = new CountDownLatch(1);
        private final CountDownLatch connectSeen = new CountDownLatch(1);
        private final AtomicReference<byte[]> capturedConnect = new AtomicReference<>();

        FakeGrpcPeer() throws IOException {
            serverSocket = new ServerSocket();
            serverSocket.bind(new InetSocketAddress("127.0.0.1", 0));
        }

        int port() {
            return serverSocket.getLocalPort();
        }

        void start() {
            executor.submit(() -> {
                ready.countDown();
                acceptLoop();
            });
        }

        boolean awaitReady(long timeout, TimeUnit unit) throws InterruptedException {
            return ready.await(timeout, unit);
        }

        boolean awaitConnect(long timeout, TimeUnit unit) throws InterruptedException {
            return connectSeen.await(timeout, unit);
        }

        byte[] capturedConnect() {
            return capturedConnect.get();
        }

        private void acceptLoop() {
            while (!serverSocket.isClosed()) {
                try {
                    Socket socket = serverSocket.accept();
                    executor.submit(() -> handle(socket));
                } catch (IOException e) {
                    if (serverSocket.isClosed()) {
                        return;
                    }
                }
            }
        }

        private void handle(Socket socket) {
            try (socket) {
                InputStream in = socket.getInputStream();
                OutputStream out = socket.getOutputStream();
                byte[] connect = GrpcJsonDeviceDriver.readFully(in, EXPECTED_CONNECT.length);
                capturedConnect.set(connect);
                out.write(SETTINGS_ACK);
                out.flush();
                connectSeen.countDown();
                while (true) {
                    byte[] header = GrpcJsonDeviceDriver.readFully(in, 2);
                    int length = ((header[0] & 0xFF) << 8) | (header[1] & 0xFF);
                    byte[] body = GrpcJsonDeviceDriver.readFully(in, length);
                    String text = new String(body, StandardCharsets.UTF_8);
                    int nl = text.indexOf('\n');
                    String name = nl >= 0 ? text.substring(nl + 1) : "world";
                    byte[] response = ("{\"message\":\"Hello " + name + "\"}")
                            .getBytes(StandardCharsets.UTF_8);
                    out.write((response.length >> 8) & 0xFF);
                    out.write(response.length & 0xFF);
                    out.write(response);
                    out.flush();
                }
            } catch (EOFException ignored) {
            } catch (IOException ignored) {
            }
        }

        @Override
        public void close() throws Exception {
            serverSocket.close();
            executor.shutdownNow();
            executor.awaitTermination(2, TimeUnit.SECONDS);
        }
    }

    private static final class StubDriverObject implements DeviceDriver.DriverObject {
        private final Map<String, String> configuration;
        final Map<String, DataRecord> variables = new ConcurrentHashMap<>();

        StubDriverObject(Map<String, String> configuration) {
            this.configuration = configuration;
        }

        @Override
        public PlatformObject deviceObject() {
            return new PlatformObject("test-grpc", "root.platform.devices.test", ObjectType.DEVICE, "Test", "", null);
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
