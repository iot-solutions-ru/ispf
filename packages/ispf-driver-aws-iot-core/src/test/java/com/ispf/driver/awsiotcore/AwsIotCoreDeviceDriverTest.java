package com.ispf.driver.awsiotcore;

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
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Loopback tests for {@link AwsIotCoreDeviceDriver} against an in-process MQTT 3.1.1 broker.
 */
class AwsIotCoreDeviceDriverTest {

    private AwsIotCoreDeviceDriver driver;
    private FakeMqttBroker broker;

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
    void metadataIsProductionReadWrite() {
        driver = new AwsIotCoreDeviceDriver();
        assertEquals("aws-iot-core", driver.metadata().id());
        assertEquals(DriverMaturity.PRODUCTION, driver.metadata().maturity());
        assertEquals(Set.of("read", "write"), driver.metadata().capabilities());
        String description = driver.metadata().description().toLowerCase(Locale.ROOT);
        assertFalse(description.contains("lab"));
        assertTrue(description.contains("mqtt"));
    }

    @Test
    void connectPacketContainsMqttProtocolAndClientIdA() {
        byte[] packet = Mqtt311Client.encodeConnect("a");
        assertEquals(0x10, packet[0] & 0xFF);
        byte[] needle = new byte[]{0x00, 0x04, 0x4D, 0x51, 0x54, 0x54, 0x04};
        assertTrue(indexOf(packet, needle) >= 0);
        assertEquals(0x00, packet[packet.length - 3] & 0xFF);
        assertEquals(0x01, packet[packet.length - 2] & 0xFF);
        assertEquals(0x61, packet[packet.length - 1] & 0xFF);
        assertArrayEquals(Mqtt311Client.CONNACK_SUCCESS, Mqtt311Client.encodeConnack(0));
        byte[] qos0 = Mqtt311Client.encodePublish("t", new byte[]{'x'}, 0, 0);
        assertEquals(0x30, qos0[0] & 0xFF);
    }

    @Test
    void subscribePublishLoopback() throws Exception {
        broker = new FakeMqttBroker();
        String topic = "dt/aws-client/sensor";
        broker.put(topic, "19.2");
        broker.start();

        StubDriverObject object = new StubDriverObject(Map.of(
                "host", "127.0.0.1",
                "port", String.valueOf(broker.port()),
                "clientId", "aws-client",
                "timeoutMs", "2000"
        ));
        driver = new AwsIotCoreDeviceDriver();
        driver.initialize(object);
        driver.connect();
        assertTrue(driver.isConnected());

        driver.readPoints(Map.of("temperature", topic));
        DataRecord temperature = object.variables.get("temperature");
        assertEquals("19.2", temperature.firstRow().get("value"));
        assertEquals(topic, temperature.firstRow().get("topic"));

        driver.writePoint("temperature", DataRecord.single(
                DataSchema.builder("v").field("value", FieldType.STRING).build(),
                Map.of("value", "20.1")
        ));
        assertEquals("20.1", broker.get(topic));
        assertEquals("20.1", object.variables.get("temperature").firstRow().get("value"));

        driver.readPoints(Map.of("temperature", topic));
        assertEquals("20.1", object.variables.get("temperature").firstRow().get("value"));
    }

    @Test
    void readPointsBeforeConnectThrows() {
        driver = new AwsIotCoreDeviceDriver();
        driver.initialize(new StubDriverObject(Map.of()));

        DriverException error = assertThrows(DriverException.class, () ->
                driver.readPoints(Map.of("s", "dt/x/y")));
        assertTrue(error.getMessage().contains("Not connected"));
    }

    @Test
    void connectFailsAgainstClosedPort() throws Exception {
        int closedPort;
        try (ServerSocket socket = new ServerSocket(0)) {
            closedPort = socket.getLocalPort();
        }

        StubDriverObject object = new StubDriverObject(Map.of(
                "host", "127.0.0.1",
                "port", String.valueOf(closedPort),
                "timeoutMs", "200",
                "clientId", "c"
        ));
        driver = new AwsIotCoreDeviceDriver();
        driver.initialize(object);

        DriverException error = assertThrows(DriverException.class, driver::connect);
        assertTrue(error.getMessage().contains("connect failed"));
    }

    private static int indexOf(byte[] haystack, byte[] needle) {
        outer:
        for (int i = 0; i <= haystack.length - needle.length; i++) {
            for (int j = 0; j < needle.length; j++) {
                if (haystack[i + j] != needle[j]) {
                    continue outer;
                }
            }
            return i;
        }
        return -1;
    }

    private static final class FakeMqttBroker implements AutoCloseable {

        private final ServerSocket serverSocket;
        private final ExecutorService executor = Executors.newCachedThreadPool(runnable -> {
            Thread thread = new Thread(runnable, "fake-mqtt-aws");
            thread.setDaemon(true);
            return thread;
        });
        private final Map<String, String> store = new ConcurrentHashMap<>();
        private final Map<String, List<ClientSession>> subs = new ConcurrentHashMap<>();
        private final AtomicInteger nextPacketId = new AtomicInteger(1);
        private volatile boolean running;

        FakeMqttBroker() throws IOException {
            serverSocket = new ServerSocket();
            serverSocket.bind(new InetSocketAddress("127.0.0.1", 0));
        }

        int port() {
            return serverSocket.getLocalPort();
        }

        void put(String topic, String value) {
            store.put(topic, value);
        }

        String get(String topic) {
            return store.get(topic);
        }

        void start() {
            running = true;
            executor.submit(this::acceptLoop);
        }

        private void acceptLoop() {
            while (running && !serverSocket.isClosed()) {
                try {
                    Socket socket = serverSocket.accept();
                    executor.submit(() -> handle(socket));
                } catch (IOException e) {
                    if (!running || serverSocket.isClosed()) {
                        return;
                    }
                }
            }
        }

        private void handle(Socket socket) {
            ClientSession session = new ClientSession(socket);
            try (socket) {
                InputStream in = socket.getInputStream();
                OutputStream out = socket.getOutputStream();
                while (running) {
                    Mqtt311Client.FixedHeader header = Mqtt311Client.readFixedHeader(in);
                    if (header == null) {
                        return;
                    }
                    byte[] body = in.readNBytes(header.remainingLength());
                    if (body.length < header.remainingLength()) {
                        return;
                    }
                    switch (header.type()) {
                        case Mqtt311Client.TYPE_CONNECT -> {
                            out.write(Mqtt311Client.encodeConnack(0));
                            out.flush();
                        }
                        case Mqtt311Client.TYPE_SUBSCRIBE -> {
                            int packetId = Mqtt311Client.parsePacketId(body);
                            String filter = Mqtt311Client.parseSubscribeTopic(body);
                            session.filters.add(filter);
                            subs.computeIfAbsent(filter, f -> new CopyOnWriteArrayList<>()).add(session);
                            out.write(Mqtt311Client.encodeSuback(packetId, 1));
                            out.flush();
                            String retained = store.get(filter);
                            if (retained != null) {
                                publishTo(session, filter, retained);
                            } else {
                                for (Map.Entry<String, String> e : store.entrySet()) {
                                    if (topicMatches(filter, e.getKey())) {
                                        publishTo(session, e.getKey(), e.getValue());
                                    }
                                }
                            }
                        }
                        case Mqtt311Client.TYPE_PUBLISH -> {
                            Mqtt311Client.ParsedPublish pub = Mqtt311Client.parsePublish(header.flags(), body);
                            store.put(pub.topic(), pub.payloadText());
                            if (pub.qos() == 1) {
                                out.write(Mqtt311Client.encodePuback(pub.packetId()));
                                out.flush();
                            }
                            fanout(pub.topic(), pub.payloadText());
                        }
                        case Mqtt311Client.TYPE_PUBACK -> {
                            // ignore
                        }
                        case Mqtt311Client.TYPE_PINGREQ -> {
                            out.write(Mqtt311Client.encodeSimple(Mqtt311Client.TYPE_PINGRESP, 0));
                            out.flush();
                        }
                        case Mqtt311Client.TYPE_DISCONNECT -> {
                            return;
                        }
                        default -> {
                            // ignore
                        }
                    }
                }
            } catch (IOException ignored) {
                // client closed
            } finally {
                for (List<ClientSession> list : subs.values()) {
                    list.remove(session);
                }
            }
        }

        private void fanout(String topic, String payload) throws IOException {
            for (Map.Entry<String, List<ClientSession>> e : subs.entrySet()) {
                if (topicMatches(e.getKey(), topic)) {
                    for (ClientSession session : e.getValue()) {
                        publishTo(session, topic, payload);
                    }
                }
            }
        }

        private void publishTo(ClientSession session, String topic, String payload) throws IOException {
            int packetId = nextPacketId.getAndIncrement() & 0xFFFF;
            byte[] packet = Mqtt311Client.encodePublish(
                    topic, payload.getBytes(StandardCharsets.UTF_8), packetId, 1);
            synchronized (session.socket) {
                OutputStream out = session.socket.getOutputStream();
                out.write(packet);
                out.flush();
            }
        }

        static boolean topicMatches(String filter, String topic) {
            return filter.equals(topic)
                    || (filter.endsWith("/#") && (topic.equals(filter.substring(0, filter.length() - 2))
                    || topic.startsWith(filter.substring(0, filter.length() - 1))));
        }

        @Override
        public void close() throws Exception {
            running = false;
            serverSocket.close();
            executor.shutdownNow();
            executor.awaitTermination(2, TimeUnit.SECONDS);
        }

        private static final class ClientSession {
            private final Socket socket;
            private final List<String> filters = new ArrayList<>();

            private ClientSession(Socket socket) {
                this.socket = socket;
            }
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
                    "test-aws-iot-core",
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
