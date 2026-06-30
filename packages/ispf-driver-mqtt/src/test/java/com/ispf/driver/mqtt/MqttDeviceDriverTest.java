package com.ispf.driver.mqtt;

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
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Properties;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MqttDeviceDriverTest {

    private Server broker;

    @AfterEach
    void tearDown() {
        if (broker != null) {
            broker.stopServer();
            broker = null;
        }
    }

    @Test
    void subscribesAndMapsPayloadViaEmbeddedBroker() throws Exception {
        int port = freePort();
        startBroker(port);
        String topic = "ispf/devices/sensor/temp";

        StubDriverObject driverObject = new StubDriverObject(Map.of(
                "brokerUrl", "tcp://127.0.0.1:" + port,
                "topicPrefix", ""
        ));
        MqttDeviceDriver driver = new MqttDeviceDriver();
        driver.initialize(driverObject);
        driver.connect();
        driver.readPoints(Map.of("temperature", topic));

        publish(topic, "23.7", port);
        awaitVariable(driverObject, "temperature");

        DataRecord record = driverObject.variables.get("temperature");
        assertEquals("23.7", record.firstRow().get("raw"));
        driver.disconnect();
    }

    @Test
    void publishWritePointViaEmbeddedBroker() throws Exception {
        int port = freePort();
        startBroker(port);
        String topic = "ispf/devices/actuator/setpoint";

        CountDownLatch received = new CountDownLatch(1);
        String[] payloadHolder = new String[1];
        MqttClient subscriber = new MqttClient(
                "tcp://127.0.0.1:" + port,
                "subscriber-" + UUID.randomUUID(),
                new MemoryPersistence()
        );
        subscriber.connect();
        subscriber.subscribe(topic, (t, message) -> {
            payloadHolder[0] = new String(message.getPayload(), StandardCharsets.UTF_8);
            received.countDown();
        });

        StubDriverObject driverObject = new StubDriverObject(Map.of(
                "brokerUrl", "tcp://127.0.0.1:" + port,
                "topicPrefix", ""
        ));
        MqttDeviceDriver driver = new MqttDeviceDriver();
        driver.initialize(driverObject);
        driver.connect();
        driver.writePoint(
                topic,
                DataRecord.single(
                        DataSchema.builder("mqttWrite").field("raw", FieldType.STRING).build(),
                        Map.of("raw", "88")
                )
        );

        assertTrue(received.await(5, TimeUnit.SECONDS), "Timed out waiting for MQTT publish on " + topic);
        assertEquals("88", payloadHolder[0]);
        driver.disconnect();
        subscriber.disconnect();
        subscriber.close();
    }

    @Test
    void writeRequiresConnection() {
        MqttDeviceDriver driver = new MqttDeviceDriver();
        driver.initialize(new StubDriverObject(Map.of()));

        DriverException error = assertThrows(DriverException.class, () ->
                driver.writePoint("topic", DataRecord.single(
                        DataSchema.builder("value").field("raw", FieldType.STRING).build(),
                        Map.of("raw", "1")
                )));
        assertTrue(error.getMessage().contains("Not connected"));
    }

    private void startBroker(int port) throws Exception {
        Properties props = new Properties();
        props.setProperty("host", "127.0.0.1");
        props.setProperty("port", String.valueOf(port));
        props.setProperty("allow_anonymous", "true");
        IConfig config = new MemoryConfig(props);
        broker = new Server();
        broker.startServer(config);
    }

    private static void publish(String topic, String payload, int port) throws Exception {
        try (MqttClient publisher = new MqttClient(
                "tcp://127.0.0.1:" + port,
                "publisher-" + UUID.randomUUID(),
                new MemoryPersistence()
        )) {
            publisher.connect();
            publisher.publish(topic, new MqttMessage(payload.getBytes(StandardCharsets.UTF_8)));
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
        private final Map<String, DataRecord> variables = new HashMap<>();

        StubDriverObject(Map<String, String> configuration) {
            this.configuration = configuration;
        }

        @Override
        public PlatformObject deviceObject() {
            return new PlatformObject(
                    "test-device",
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
