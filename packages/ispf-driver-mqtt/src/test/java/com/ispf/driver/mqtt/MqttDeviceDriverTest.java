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
import java.util.concurrent.atomic.AtomicInteger;

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
    void fifoIngressHandlesEachMessageWithoutCoalesceBuffer() throws Exception {
        int port = freePort();
        startBroker(port);
        String topic = "ispf/loadtest/00001/temperature";

        CountingStubDriverObject driverObject = new CountingStubDriverObject(Map.of(
                "brokerUrl", "tcp://127.0.0.1:" + port,
                "topicPrefix", "",
                "ingressCoalesceEnabled", "false",
                "callbackThreads", "4",
                "callbackQueueCapacity", "10000"
        ));
        MqttDeviceDriver driver = new MqttDeviceDriver();
        driver.initialize(driverObject);
        driver.connect();
        driver.readPoints(Map.of("temperature", topic));

        publish(topic, "42.0", port);
        awaitVariable(driverObject, "temperature");
        assertEquals(1, driverObject.updateCount.get());
        driver.disconnect();
    }

    @Test
    void eventJournalOnlyModeUsesFifoWithoutCoalesce() throws Exception {
        int port = freePort();
        startBroker(port);
        String topic = "ispf/loadtest/shared/temperature";

        CountingStubDriverObject driverObject = new CountingStubDriverObject(Map.of(
                "brokerUrl", "tcp://127.0.0.1:" + port,
                "topicPrefix", "",
                "telemetryPublishMode", "EVENT_JOURNAL_ONLY"
        ));
        MqttDeviceDriver driver = new MqttDeviceDriver();
        driver.initialize(driverObject);
        driver.connect();
        driver.readPoints(Map.of("temperature", topic));

        publish(topic, "1", port);
        publish(topic, "2", port);
        assertTrue(driverObject.awaitUpdates(2, 5, TimeUnit.SECONDS));
        driver.disconnect();
    }

    @Test
    void secondConnectShutsDownPreviousIngress() throws Exception {
        int port = freePort();
        startBroker(port);
        String topic = "ispf/loadtest/00002/temperature";

        CountingStubDriverObject driverObject = new CountingStubDriverObject(Map.of(
                "brokerUrl", "tcp://127.0.0.1:" + port,
                "topicPrefix", "",
                "ingressCoalesceEnabled", "false"
        ));
        MqttDeviceDriver driver = new MqttDeviceDriver();
        driver.initialize(driverObject);
        driver.connect();
        driver.readPoints(Map.of("temperature", topic));

        publish(topic, "first", port);
        assertTrue(driverObject.awaitUpdates(1, 5, TimeUnit.SECONDS));

        driver.connect();
        driver.readPoints(Map.of("temperature", topic));
        publish(topic, "second", port);
        assertTrue(driverObject.awaitUpdates(2, 5, TimeUnit.SECONDS));
        driver.disconnect();
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

    @Test
    void eventToVariableCreatesPerTopicVariables() throws Exception {
        int port = freePort();
        startBroker(port);
        String topicA = "ispf/devices/site-a/temperature";
        String topicB = "ispf/devices/site-b/temperature";

        StubDriverObject driverObject = new StubDriverObject(Map.of(
                "brokerUrl", "tcp://127.0.0.1:" + port,
                "topicPrefix", "",
                "eventToVariable", "true"
        ));
        MqttDeviceDriver driver = new MqttDeviceDriver();
        driver.initialize(driverObject);
        driver.connect();
        driver.readPoints(Map.of("catchAll", "#"));

        publish(topicA, "21.1", port);
        publish(topicB, "22.2", port);
        awaitVariable(driverObject, "ispf_devices_site_a_temperature");
        awaitVariable(driverObject, "ispf_devices_site_b_temperature");

        assertEquals("21.1", driverObject.variables.get("ispf_devices_site_a_temperature").firstRow().get("raw"));
        assertEquals("22.2", driverObject.variables.get("ispf_devices_site_b_temperature").firstRow().get("raw"));
        assertEquals(topicA, driverObject.variables.get("ispf_devices_site_a_temperature").firstRow().get("topic"));
        driver.disconnect();
    }

    @Test
    void eventToVariableDisabledUsesLastMessage() throws Exception {
        int port = freePort();
        startBroker(port);
        String topic = "ispf/devices/unmapped/temp";

        StubDriverObject driverObject = new StubDriverObject(Map.of(
                "brokerUrl", "tcp://127.0.0.1:" + port,
                "topicPrefix", "",
                "eventToVariable", "false"
        ));
        MqttDeviceDriver driver = new MqttDeviceDriver();
        driver.initialize(driverObject);
        driver.connect();
        driver.readPoints(Map.of("catchAll", "#"));

        publish(topic, "99.0", port);
        awaitVariable(driverObject, "lastMessage");

        assertEquals("99.0", driverObject.variables.get("lastMessage").firstRow().get("raw"));
        driver.disconnect();
    }

    @Test
    void eventToVariableIgnoredWhenIngressVariableSet() throws Exception {
        int port = freePort();
        startBroker(port);
        String topic = "ispf/devices/sensor/temp";

        StubDriverObject driverObject = new StubDriverObject(Map.of(
                "brokerUrl", "tcp://127.0.0.1:" + port,
                "topicPrefix", "",
                "eventToVariable", "true",
                "ingressVariable", "lastIngress"
        ));
        MqttDeviceDriver driver = new MqttDeviceDriver();
        driver.initialize(driverObject);
        driver.connect();
        driver.readPoints(Map.of("catchAll", "#"));

        publish(topic, "33.3", port);
        awaitVariable(driverObject, "lastIngress");

        assertEquals("33.3", driverObject.variables.get("lastIngress").firstRow().get("raw"));
        assertEquals(topic, driverObject.variables.get("lastIngress").firstRow().get("topic"));
        driver.disconnect();
    }

    @Test
    void explicitMappingTakesPrecedenceOverEventToVariable() throws Exception {
        int port = freePort();
        startBroker(port);
        String topic = "ispf/devices/sensor/temp";

        StubDriverObject driverObject = new StubDriverObject(Map.of(
                "brokerUrl", "tcp://127.0.0.1:" + port,
                "topicPrefix", "",
                "eventToVariable", "true"
        ));
        MqttDeviceDriver driver = new MqttDeviceDriver();
        driver.initialize(driverObject);
        driver.connect();
        driver.readPoints(Map.of("temperature", topic));

        publish(topic, "18.5", port);
        awaitVariable(driverObject, "temperature");

        assertEquals("18.5", driverObject.variables.get("temperature").firstRow().get("raw"));
        driver.disconnect();
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

    private static final class CountingStubDriverObject extends StubDriverObject {

        private final AtomicInteger updateCount = new AtomicInteger();

        CountingStubDriverObject(Map<String, String> configuration) {
            super(configuration);
        }

        @Override
        public void updateVariable(String name, DataRecord value) {
            updateCount.incrementAndGet();
            super.updateVariable(name, value);
        }

        boolean awaitUpdates(int minimum, long timeout, TimeUnit unit) throws InterruptedException {
            long deadline = System.nanoTime() + unit.toNanos(timeout);
            while (updateCount.get() < minimum && System.nanoTime() < deadline) {
                Thread.sleep(10);
            }
            return updateCount.get() >= minimum;
        }
    }

    private static class StubDriverObject implements DeviceDriver.DriverObject {

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
