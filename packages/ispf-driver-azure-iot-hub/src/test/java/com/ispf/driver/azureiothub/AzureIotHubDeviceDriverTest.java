package com.ispf.driver.azureiothub;

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
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Loopback tests for {@link AzureIotHubDeviceDriver} against an in-process MQTT 3.1.1 lab broker.
 */
class AzureIotHubDeviceDriverTest {

    private AzureIotHubDeviceDriver driver;
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
        driver = new AzureIotHubDeviceDriver();
        assertEquals("azure-iot-hub", driver.metadata().id());
        assertEquals(DriverMaturity.PRODUCTION, driver.metadata().maturity());
        assertEquals(Set.of("read", "write"), driver.metadata().capabilities());
        assertTrue(driver.metadata().description().toLowerCase().contains("lab"));
    }

    @Test
    void telemetryPublishAndC2dReadLoopback() throws Exception {
        broker = new FakeMqttBroker();
        broker.start();

        String deviceId = "dev-1";
        String c2dTopic = "devices/" + deviceId + "/messages/devicebound/cmd";
        broker.put(c2dTopic, "reboot");

        StubDriverObject object = new StubDriverObject(Map.of(
                "host", "127.0.0.1",
                "port", String.valueOf(broker.port()),
                "deviceId", deviceId,
                "timeoutMs", "2000"
        ));
        driver = new AzureIotHubDeviceDriver();
        driver.initialize(object);
        driver.connect();
        assertTrue(driver.isConnected());

        driver.readPoints(Map.of("command", "cmd"));
        DataRecord command = object.variables.get("command");
        assertEquals("reboot", command.firstRow().get("value"));
        assertEquals(c2dTopic, command.firstRow().get("topic"));

        driver.writePoint("telemetry", DataRecord.single(
                DataSchema.builder("v").field("value", FieldType.STRING).build(),
                Map.of("value", "21.5")
        ));
        String eventsTopic = "devices/" + deviceId + "/messages/events/telemetry";
        assertEquals("21.5", broker.get(eventsTopic));
        assertEquals("21.5", object.variables.get("telemetry").firstRow().get("value"));
        assertEquals(eventsTopic, object.variables.get("telemetry").firstRow().get("topic"));
    }

    @Test
    void resolveTelemetryTopicUsesEventsPrefix() {
        driver = new AzureIotHubDeviceDriver();
        driver.initialize(new StubDriverObject(Map.of("deviceId", "x")));
        assertEquals("devices/x/messages/events/", driver.resolveTelemetryTopic(""));
        assertEquals("devices/x/messages/events/temp", driver.resolveTelemetryTopic("temp"));
        assertEquals("devices/x/messages/events/custom",
                driver.resolveTelemetryTopic("devices/x/messages/events/custom"));
    }

    @Test
    void readPointsBeforeConnectThrows() {
        driver = new AzureIotHubDeviceDriver();
        driver.initialize(new StubDriverObject(Map.of()));

        DriverException error = assertThrows(DriverException.class, () ->
                driver.readPoints(Map.of("c", "cmd")));
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
                "deviceId", "d"
        ));
        driver = new AzureIotHubDeviceDriver();
        driver.initialize(object);

        DriverException error = assertThrows(DriverException.class, driver::connect);
        assertTrue(error.getMessage().contains("connect failed"));
    }

    private static final class FakeMqttBroker implements AutoCloseable {

        private final ServerSocket serverSocket;
        private final ExecutorService executor = Executors.newCachedThreadPool(runnable -> {
            Thread thread = new Thread(runnable, "fake-mqtt-azure");
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
                    Mqtt311Lab.FixedHeader header = Mqtt311Lab.readFixedHeader(in);
                    if (header == null) {
                        return;
                    }
                    byte[] body = in.readNBytes(header.remainingLength());
                    if (body.length < header.remainingLength()) {
                        return;
                    }
                    switch (header.type()) {
                        case Mqtt311Lab.TYPE_CONNECT -> {
                            out.write(Mqtt311Lab.encodeConnack(0));
                            out.flush();
                        }
                        case Mqtt311Lab.TYPE_SUBSCRIBE -> {
                            int packetId = Mqtt311Lab.parsePacketId(body);
                            String filter = Mqtt311Lab.parseSubscribeTopic(body);
                            session.filters.add(filter);
                            subs.computeIfAbsent(filter, f -> new CopyOnWriteArrayList<>()).add(session);
                            out.write(Mqtt311Lab.encodeSuback(packetId, 1));
                            out.flush();
                            deliverRetained(session, filter);
                        }
                        case Mqtt311Lab.TYPE_PUBLISH -> {
                            Mqtt311Lab.ParsedPublish pub = Mqtt311Lab.parsePublish(header.flags(), body);
                            String text = new String(pub.payload(), StandardCharsets.UTF_8);
                            store.put(pub.topic(), text);
                            if (pub.qos() == 1) {
                                out.write(Mqtt311Lab.encodePuback(pub.packetId()));
                                out.flush();
                            }
                            fanout(pub.topic(), text);
                        }
                        case Mqtt311Lab.TYPE_PUBACK -> {
                            // ignore
                        }
                        case Mqtt311Lab.TYPE_PINGREQ -> {
                            out.write(Mqtt311Lab.encodeSimple(Mqtt311Lab.TYPE_PINGRESP, 0));
                            out.flush();
                        }
                        case Mqtt311Lab.TYPE_DISCONNECT -> {
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

        private void deliverRetained(ClientSession session, String filter) throws IOException {
            for (Map.Entry<String, String> e : store.entrySet()) {
                if (topicMatches(filter, e.getKey())) {
                    publishTo(session, e.getKey(), e.getValue());
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
            byte[] packet = Mqtt311Lab.encodePublish(
                    topic, payload.getBytes(StandardCharsets.UTF_8), packetId, 1);
            synchronized (session.socket) {
                OutputStream out = session.socket.getOutputStream();
                out.write(packet);
                out.flush();
            }
        }

        static boolean topicMatches(String filter, String topic) {
            if (filter.equals(topic)) {
                return true;
            }
            if (filter.endsWith("/#")) {
                String prefix = filter.substring(0, filter.length() - 1); // keep trailing /
                String base = filter.substring(0, filter.length() - 2);
                return topic.equals(base) || topic.startsWith(prefix);
            }
            if (filter.endsWith("/+")) {
                String prefix = filter.substring(0, filter.length() - 1);
                if (!topic.startsWith(prefix)) {
                    return false;
                }
                String rest = topic.substring(prefix.length());
                return !rest.isEmpty() && !rest.contains("/");
            }
            return false;
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
                    "test-azure-iot-hub",
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
