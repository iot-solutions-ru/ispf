package com.ispf.driver.pulsar;

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
import java.util.Arrays;
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

/**
 * Loopback tests for {@link PulsarDeviceDriver} against an in-process lab TCP peer.
 */
class PulsarDeviceDriverTest {

    /** Handwritten CONNECT frame — do not build via encoder. */
    private static final byte[] EXPECTED_CONNECT = {
            0x00, 0x00, 0x00, 0x0A, 0x00, 0x00, 0x00, 0x02, 0x08, 0x02
    };

    private static final byte[] CONNECTED_REPLY = {
            0x00, 0x00, 0x00, 0x0A, 0x00, 0x00, 0x00, 0x02, 0x08, 0x03
    };

    private static final byte OP_GET = 0x01;
    private static final byte OP_PUB = 0x02;
    private static final byte OP_MSG = 0x03;
    private static final byte OP_NIL = 0x04;
    private static final byte OP_OK = 0x05;
    private static final byte OP_ERR = 0x06;

    private PulsarDeviceDriver driver;
    private FakePulsarBroker broker;

    @AfterEach
    void tearDown() throws Exception {
        if (driver != null) {
            driver.disconnect();
            driver = null;
        }
        if (broker != null) {
            broker.close();
            broker = null;
        }
    }

    @Test
    void metadataIsBetaLabConnectNotProtobufBroker() {
        assertEquals("pulsar", new PulsarDeviceDriver().metadata().id());
        assertEquals(DriverMaturity.BETA, new PulsarDeviceDriver().metadata().maturity());
        assertTrue(new PulsarDeviceDriver().metadata().supportsWrite());
        String description = new PulsarDeviceDriver().metadata().description().toLowerCase(Locale.ROOT);
        assertTrue(description.contains("lab"));
        assertTrue(description.contains("connect"));
        assertTrue(description.contains("not") && description.contains("protobuf"));
    }

    @Test
    void connectWritesBaseCommandConnectExpectsConnected() throws Exception {
        broker = new FakePulsarBroker();
        broker.start();
        assertTrue(broker.awaitReady(2, TimeUnit.SECONDS));

        StubDriverObject object = new StubDriverObject(Map.of(
                "host", "127.0.0.1",
                "port", String.valueOf(broker.port()),
                "timeoutMs", "2000"
        ));
        driver = new PulsarDeviceDriver();
        driver.initialize(object);
        driver.connect();
        assertTrue(driver.isConnected());
        assertTrue(broker.awaitConnect(2, TimeUnit.SECONDS));
        assertArrayEquals(EXPECTED_CONNECT, broker.capturedConnect());
        assertEquals(10, broker.capturedConnect().length);
    }

    @Test
    void publishesAndGetsTopicPayloads() throws Exception {
        broker = new FakePulsarBroker();
        broker.put("sensors/temp", "23.5");
        broker.start();
        assertTrue(broker.awaitReady(2, TimeUnit.SECONDS));

        StubDriverObject object = new StubDriverObject(Map.of(
                "host", "127.0.0.1",
                "port", String.valueOf(broker.port()),
                "timeoutMs", "2000"
        ));
        driver = new PulsarDeviceDriver();
        driver.initialize(object);
        driver.connect();
        assertTrue(driver.isConnected());

        driver.readPoints(Map.of("temperature", "sensors/temp"));
        DataRecord temperature = object.variables.get("temperature");
        assertEquals("23.5", temperature.firstRow().get("value"));
        assertEquals("sensors/temp", temperature.firstRow().get("topic"));

        driver.writePoint("temperature", DataRecord.single(
                DataSchema.builder("v").field("value", FieldType.STRING).build(),
                Map.of("value", "24.1")
        ));
        assertEquals("24.1", broker.get("sensors/temp"));
        assertEquals("24.1", object.variables.get("temperature").firstRow().get("value"));

        driver.readPoints(Map.of("temperature", "sensors/temp"));
        assertEquals("24.1", object.variables.get("temperature").firstRow().get("value"));
    }

    @Test
    void missingTopicReturnsEmptyString() throws Exception {
        broker = new FakePulsarBroker();
        broker.start();
        assertTrue(broker.awaitReady(2, TimeUnit.SECONDS));

        StubDriverObject object = new StubDriverObject(Map.of(
                "host", "127.0.0.1",
                "port", String.valueOf(broker.port())
        ));
        driver = new PulsarDeviceDriver();
        driver.initialize(object);
        driver.connect();

        driver.readPoints(Map.of("missing", "no-such-topic"));
        assertEquals("", object.variables.get("missing").firstRow().get("value"));
    }

    @Test
    void readPointsBeforeConnectThrows() {
        driver = new PulsarDeviceDriver();
        driver.initialize(new StubDriverObject(Map.of()));

        DriverException error = assertThrows(DriverException.class, () ->
                driver.readPoints(Map.of("t", "topic")));
        assertTrue(error.getMessage().contains("Not connected"));
    }

    private static final class FakePulsarBroker implements AutoCloseable {

        private final ServerSocket serverSocket;
        private final ExecutorService executor = Executors.newCachedThreadPool(runnable -> {
            Thread thread = new Thread(runnable, "fake-pulsar-broker");
            thread.setDaemon(true);
            return thread;
        });
        private final Map<String, String> topics = new ConcurrentHashMap<>();
        private final CountDownLatch ready = new CountDownLatch(1);
        private final CountDownLatch connectSeen = new CountDownLatch(1);
        private final AtomicReference<byte[]> capturedConnect = new AtomicReference<>();

        FakePulsarBroker() throws IOException {
            serverSocket = new ServerSocket();
            serverSocket.bind(new InetSocketAddress("127.0.0.1", 0));
        }

        int port() {
            return serverSocket.getLocalPort();
        }

        void put(String topic, String payload) {
            topics.put(topic, payload);
        }

        String get(String topic) {
            return topics.get(topic);
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
                byte[] connect = PulsarDeviceDriver.readFully(in, EXPECTED_CONNECT.length);
                capturedConnect.set(connect);
                out.write(CONNECTED_REPLY);
                out.flush();
                connectSeen.countDown();
                while (true) {
                    byte[] lenHdr = PulsarDeviceDriver.readFully(in, 2);
                    int bodyLen = ((lenHdr[0] & 0xFF) << 8) | (lenHdr[1] & 0xFF);
                    byte[] body = PulsarDeviceDriver.readFully(in, bodyLen);
                    byte op = body[0];
                    int topicLen = ((body[1] & 0xFF) << 8) | (body[2] & 0xFF);
                    String topic = new String(body, 3, topicLen, StandardCharsets.UTF_8);
                    int payloadOffset = 3 + topicLen;
                    int payloadLen = ((body[payloadOffset] & 0xFF) << 8) | (body[payloadOffset + 1] & 0xFF);
                    byte[] payload = Arrays.copyOfRange(body, payloadOffset + 2, payloadOffset + 2 + payloadLen);
                    if (op == OP_PUB) {
                        topics.put(topic, new String(payload, StandardCharsets.UTF_8));
                        writeLab(out, OP_OK, topic, new byte[0]);
                    } else if (op == OP_GET) {
                        String stored = topics.get(topic);
                        if (stored == null) {
                            writeLab(out, OP_NIL, topic, new byte[0]);
                        } else {
                            writeLab(out, OP_MSG, topic, stored.getBytes(StandardCharsets.UTF_8));
                        }
                    } else {
                        writeLab(out, OP_ERR, topic, "unknown".getBytes(StandardCharsets.UTF_8));
                    }
                }
            } catch (EOFException ignored) {
            } catch (IOException ignored) {
            }
        }

        private static void writeLab(OutputStream out, byte op, String topic, byte[] payload)
                throws IOException {
            byte[] topicBytes = topic.getBytes(StandardCharsets.UTF_8);
            int body = 1 + 2 + topicBytes.length + 2 + payload.length;
            out.write((body >> 8) & 0xFF);
            out.write(body & 0xFF);
            out.write(op);
            out.write((topicBytes.length >> 8) & 0xFF);
            out.write(topicBytes.length & 0xFF);
            out.write(topicBytes);
            out.write((payload.length >> 8) & 0xFF);
            out.write(payload.length & 0xFF);
            out.write(payload);
            out.flush();
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
        private final Map<String, DataRecord> variables = new ConcurrentHashMap<>();

        StubDriverObject(Map<String, String> configuration) {
            this.configuration = configuration;
        }

        @Override
        public PlatformObject deviceObject() {
            return new PlatformObject(
                    "test-pulsar",
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
