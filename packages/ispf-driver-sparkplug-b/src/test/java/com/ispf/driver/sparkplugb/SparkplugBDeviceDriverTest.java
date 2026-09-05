package com.ispf.driver.sparkplugb;

import com.ispf.core.model.DataRecord;
import com.ispf.core.model.DataSchema;
import com.ispf.core.model.FieldType;
import com.ispf.core.object.ObjectType;
import com.ispf.core.object.PlatformObject;
import com.ispf.driver.DeviceDriver;
import com.ispf.driver.DriverException;
import io.moquette.broker.Server;
import io.moquette.broker.config.IConfig;
import io.moquette.broker.config.MemoryConfig;
import org.eclipse.paho.client.mqttv3.MqttClient;
import org.eclipse.paho.client.mqttv3.MqttMessage;
import org.eclipse.paho.client.mqttv3.persist.MemoryPersistence;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.net.ServerSocket;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Properties;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Loopback tests for {@link SparkplugBDeviceDriver} against an embedded Moquette broker.
 */
class SparkplugBDeviceDriverTest {

    private Server broker;
    private SparkplugBDeviceDriver driver;

    @AfterEach
    void tearDown() throws Exception {
        if (driver != null) {
            driver.disconnect();
            driver = null;
        }
        if (broker != null) {
            broker.stopServer();
            broker = null;
        }
    }

    @Test
    void receivesDdataMetricsAndPublishesDcmd() throws Exception {
        int port = freePort();
        startBroker(port);
        String group = "LabGroup";

        StubDriverObject object = new StubDriverObject(Map.of(
                "brokerUrl", "tcp://127.0.0.1:" + port,
                "groupId", group,
                "edgeNode", "NodeA",
                "deviceId", "Dev1"
        ));
        driver = new SparkplugBDeviceDriver();
        driver.initialize(object);
        driver.connect();
        assertTrue(driver.isConnected());
        driver.readPoints(Map.of(
                "temperature", "temp",
                "running", "running"
        ));

        byte[] birth = SparkplugBCodec.encode(new SparkplugBCodec.Payload(
                System.currentTimeMillis(),
                List.of(
                        new SparkplugBCodec.Metric("temp", SparkplugBCodec.DATATYPE_FLOAT, 21.5f),
                        new SparkplugBCodec.Metric("running", SparkplugBCodec.DATATYPE_BOOLEAN, true)
                ),
                0L
        ));
        publish("spBv1.0/" + group + "/DBIRTH/NodeA/Dev1", birth, port);
        awaitVariable(object, "temperature");
        awaitVariable(object, "running");
        assertEquals("21.5", object.variables.get("temperature").firstRow().get("value"));
        assertEquals("true", object.variables.get("running").firstRow().get("value"));

        CountDownLatch dcmdLatch = new CountDownLatch(1);
        AtomicReference<byte[]> dcmdPayload = new AtomicReference<>();
        MqttClient subscriber = new MqttClient(
                "tcp://127.0.0.1:" + port,
                "dcmd-sub-" + UUID.randomUUID(),
                new MemoryPersistence()
        );
        subscriber.connect();
        subscriber.subscribe("spBv1.0/" + group + "/DCMD/#", (topic, message) -> {
            dcmdPayload.set(message.getPayload());
            dcmdLatch.countDown();
        });

        driver.writePoint("temperature", DataRecord.single(
                DataSchema.builder("v").field("value", FieldType.STRING).build(),
                Map.of("value", "30.0")
        ));
        assertTrue(dcmdLatch.await(5, TimeUnit.SECONDS), "Timed out waiting for DCMD");
        SparkplugBCodec.Payload decoded = SparkplugBCodec.decode(dcmdPayload.get());
        assertEquals(1, decoded.metrics().size());
        assertEquals("temp", decoded.metrics().get(0).name());
        assertEquals(30.0f, ((Number) decoded.metrics().get(0).value()).floatValue(), 0.01f);

        subscriber.disconnect();
        subscriber.close();
    }

    @Test
    void codecRoundTripStringIntBool() {
        SparkplugBCodec.Payload original = new SparkplugBCodec.Payload(
                123L,
                List.of(
                        new SparkplugBCodec.Metric("name", SparkplugBCodec.DATATYPE_STRING, "pump-1"),
                        new SparkplugBCodec.Metric("count", SparkplugBCodec.DATATYPE_INT32, 7),
                        new SparkplugBCodec.Metric("ok", SparkplugBCodec.DATATYPE_BOOLEAN, false)
                ),
                9L
        );
        SparkplugBCodec.Payload decoded = SparkplugBCodec.decode(SparkplugBCodec.encode(original));
        assertEquals(123L, decoded.timestamp());
        assertEquals(9L, decoded.seq());
        assertEquals(3, decoded.metrics().size());
        assertEquals("pump-1", decoded.metrics().get(0).value());
        assertEquals(7, ((Number) decoded.metrics().get(1).value()).intValue());
        assertEquals(false, decoded.metrics().get(2).value());
    }

    @Test
    void readPointsBeforeConnectThrows() {
        driver = new SparkplugBDeviceDriver();
        driver.initialize(new StubDriverObject(Map.of()));

        DriverException error = assertThrows(DriverException.class, () ->
                driver.readPoints(Map.of("temp", "temp")));
        assertTrue(error.getMessage().contains("Not connected"));
    }

    private void startBroker(int port) throws Exception {
        Properties props = new Properties();
        props.setProperty("host", "127.0.0.1");
        props.setProperty("port", String.valueOf(port));
        props.setProperty("allow_anonymous", "true");
        props.setProperty("persistence_enabled", "false");
        props.setProperty("data_path", System.getProperty("java.io.tmpdir") + "/ispf-sparkplug-moquette-" + port);
        IConfig config = new MemoryConfig(props);
        broker = new Server();
        broker.startServer(config);
    }

    private static void publish(String topic, byte[] payload, int port) throws Exception {
        try (MqttClient publisher = new MqttClient(
                "tcp://127.0.0.1:" + port,
                "publisher-" + UUID.randomUUID(),
                new MemoryPersistence()
        )) {
            publisher.connect();
            publisher.publish(topic, new MqttMessage(payload));
            publisher.disconnect();
        }
    }

    private static void awaitVariable(StubDriverObject driverObject, String name) throws InterruptedException {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
        while (!driverObject.variables.containsKey(name) && System.nanoTime() < deadline) {
            Thread.sleep(50);
        }
        if (!driverObject.variables.containsKey(name)) {
            throw new AssertionError("Timed out waiting for variable " + name);
        }
    }

    private static int freePort() throws Exception {
        try (ServerSocket socket = new ServerSocket(0)) {
            return socket.getLocalPort();
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
                    "test-sparkplug",
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
