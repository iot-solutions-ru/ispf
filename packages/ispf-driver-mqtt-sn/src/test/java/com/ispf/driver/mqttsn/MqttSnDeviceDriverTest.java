package com.ispf.driver.mqttsn;

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
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Loopback tests for {@link MqttSnDeviceDriver} against an in-process fake MQTT-SN gateway.
 */
class MqttSnDeviceDriverTest {

    private MqttSnDeviceDriver driver;
    private FakeMqttSnGateway gateway;

    @AfterEach
    void tearDown() throws Exception {
        if (driver != null) {
            driver.disconnect();
            driver = null;
        }
        if (gateway != null) {
            gateway.close();
            gateway = null;
        }
    }

    @Test
    void metadataIsProductionReadWrite() {
        driver = new MqttSnDeviceDriver();
        assertEquals("mqtt-sn", driver.metadata().id());
        assertEquals(DriverMaturity.PRODUCTION, driver.metadata().maturity());
        assertEquals(Set.of("read", "write"), driver.metadata().capabilities());
    }

    @Test
    void subscribePublishLoopback() throws Exception {
        gateway = new FakeMqttSnGateway();
        gateway.put("sensors/temp", "21.5");
        gateway.start();

        StubDriverObject object = new StubDriverObject(Map.of(
                "host", "127.0.0.1",
                "port", String.valueOf(gateway.port()),
                "timeoutMs", "2000",
                "clientId", "test-client"
        ));
        driver = new MqttSnDeviceDriver();
        driver.initialize(object);
        driver.connect();
        assertTrue(driver.isConnected());

        driver.readPoints(Map.of("temperature", "sensors/temp"));
        DataRecord temperature = object.variables.get("temperature");
        assertEquals("21.5", temperature.firstRow().get("value"));
        assertEquals("sensors/temp", temperature.firstRow().get("topic"));

        driver.writePoint("temperature", DataRecord.single(
                DataSchema.builder("v").field("value", FieldType.STRING).build(),
                Map.of("value", "22.0")
        ));
        assertEquals("22.0", gateway.get("sensors/temp"));
        assertEquals("22.0", object.variables.get("temperature").firstRow().get("value"));

        driver.readPoints(Map.of("temperature", "sensors/temp"));
        assertEquals("22.0", object.variables.get("temperature").firstRow().get("value"));
    }

    @Test
    void readPointsBeforeConnectThrows() {
        driver = new MqttSnDeviceDriver();
        driver.initialize(new StubDriverObject(Map.of()));

        DriverException error = assertThrows(DriverException.class, () ->
                driver.readPoints(Map.of("t", "topic")));
        assertTrue(error.getMessage().contains("Not connected"));
    }

    @Test
    void encodeConnectIncludesClientId() {
        byte[] frame = MqttSnDeviceDriver.encodeConnect("abc", (short) 30);
        MqttSnDeviceDriver.ParsedMessage msg = MqttSnDeviceDriver.parse(frame);
        assertEquals(MqttSnDeviceDriver.MSG_CONNECT, msg.type());
        assertEquals("abc", new String(msg.body(), 4, msg.body().length - 4, StandardCharsets.UTF_8));
    }

    private static final class FakeMqttSnGateway implements AutoCloseable {

        private final DatagramSocket socket;
        private final ExecutorService executor = Executors.newSingleThreadExecutor(runnable -> {
            Thread thread = new Thread(runnable, "fake-mqtt-sn");
            thread.setDaemon(true);
            return thread;
        });
        private final Map<String, String> store = new ConcurrentHashMap<>();
        private final Map<String, Integer> topicIds = new ConcurrentHashMap<>();
        private final Map<Integer, String> topicNames = new ConcurrentHashMap<>();
        private final AtomicInteger nextTopicId = new AtomicInteger(1);
        private volatile boolean running;

        FakeMqttSnGateway() throws IOException {
            socket = new DatagramSocket(new InetSocketAddress("127.0.0.1", 0));
        }

        int port() {
            return socket.getLocalPort();
        }

        void put(String topic, String value) {
            store.put(topic, value);
        }

        String get(String topic) {
            return store.get(topic);
        }

        void start() {
            running = true;
            executor.submit(this::loop);
        }

        private void loop() {
            byte[] buf = new byte[2048];
            while (running && !socket.isClosed()) {
                try {
                    DatagramPacket packet = new DatagramPacket(buf, buf.length);
                    socket.receive(packet);
                    byte[] frame = new byte[packet.getLength()];
                    System.arraycopy(packet.getData(), 0, frame, 0, packet.getLength());
                    handle(frame, packet.getSocketAddress());
                } catch (IOException e) {
                    if (!running || socket.isClosed()) {
                        return;
                    }
                }
            }
        }

        private void handle(byte[] frame, java.net.SocketAddress client) throws IOException {
            MqttSnDeviceDriver.ParsedMessage msg = MqttSnDeviceDriver.parse(frame);
            if (msg == null) {
                return;
            }
            switch (msg.type()) {
                case MqttSnDeviceDriver.MSG_CONNECT -> reply(client, wrap(
                        MqttSnDeviceDriver.MSG_CONNACK,
                        new byte[]{MqttSnDeviceDriver.RC_ACCEPTED}
                ));
                case MqttSnDeviceDriver.MSG_REGISTER -> {
                    ByteBuffer body = ByteBuffer.wrap(msg.body());
                    body.getShort();
                    int msgId = body.getShort() & 0xFFFF;
                    byte[] nameBytes = new byte[body.remaining()];
                    body.get(nameBytes);
                    String topic = new String(nameBytes, StandardCharsets.UTF_8);
                    int topicId = topicIds.computeIfAbsent(topic, t -> {
                        int id = nextTopicId.getAndIncrement();
                        topicNames.put(id, t);
                        return id;
                    });
                    ByteBuffer ack = ByteBuffer.allocate(5);
                    ack.putShort((short) topicId);
                    ack.putShort((short) msgId);
                    ack.put(MqttSnDeviceDriver.RC_ACCEPTED);
                    reply(client, wrap(MqttSnDeviceDriver.MSG_REGACK, ack.array()));
                }
                case MqttSnDeviceDriver.MSG_SUBSCRIBE -> {
                    ByteBuffer body = ByteBuffer.wrap(msg.body());
                    body.get(); // flags
                    int msgId = body.getShort() & 0xFFFF;
                    byte[] nameBytes = new byte[body.remaining()];
                    body.get(nameBytes);
                    String topic = new String(nameBytes, StandardCharsets.UTF_8);
                    int topicId = topicIds.computeIfAbsent(topic, t -> {
                        int id = nextTopicId.getAndIncrement();
                        topicNames.put(id, t);
                        return id;
                    });
                    ByteBuffer ack = ByteBuffer.allocate(6);
                    ack.put(MqttSnDeviceDriver.FLAG_QOS1);
                    ack.putShort((short) topicId);
                    ack.putShort((short) msgId);
                    ack.put(MqttSnDeviceDriver.RC_ACCEPTED);
                    reply(client, wrap(MqttSnDeviceDriver.MSG_SUBACK, ack.array()));
                    String retained = store.get(topic);
                    if (retained != null) {
                        byte[] data = retained.getBytes(StandardCharsets.UTF_8);
                        ByteBuffer pub = ByteBuffer.allocate(5 + data.length);
                        pub.put(MqttSnDeviceDriver.FLAG_QOS1);
                        pub.putShort((short) topicId);
                        pub.putShort((short) 0);
                        pub.put(data);
                        reply(client, wrap(MqttSnDeviceDriver.MSG_PUBLISH, pub.array()));
                    }
                }
                case MqttSnDeviceDriver.MSG_PUBLISH -> {
                    ByteBuffer body = ByteBuffer.wrap(msg.body());
                    body.get(); // flags
                    int topicId = body.getShort() & 0xFFFF;
                    int msgId = body.getShort() & 0xFFFF;
                    byte[] data = new byte[body.remaining()];
                    body.get(data);
                    String topic = topicNames.getOrDefault(topicId, "topicId:" + topicId);
                    store.put(topic, new String(data, StandardCharsets.UTF_8));
                    ByteBuffer ack = ByteBuffer.allocate(5);
                    ack.putShort((short) topicId);
                    ack.putShort((short) msgId);
                    ack.put(MqttSnDeviceDriver.RC_ACCEPTED);
                    reply(client, wrap(MqttSnDeviceDriver.MSG_PUBACK, ack.array()));
                }
                case MqttSnDeviceDriver.MSG_DISCONNECT -> {
                    // ignore
                }
                default -> {
                    // ignore unknown
                }
            }
        }

        private void reply(java.net.SocketAddress client, byte[] frame) throws IOException {
            socket.send(new DatagramPacket(frame, frame.length, client));
        }

        private static byte[] wrap(byte type, byte[] body) {
            return MqttSnDeviceDriver.wrap(type, body);
        }

        @Override
        public void close() throws Exception {
            running = false;
            socket.close();
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
                    "test-mqtt-sn",
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
