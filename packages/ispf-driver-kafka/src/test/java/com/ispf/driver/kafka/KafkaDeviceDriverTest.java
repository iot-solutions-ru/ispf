package com.ispf.driver.kafka;

import com.ispf.core.model.DataRecord;
import com.ispf.core.object.ObjectType;
import com.ispf.core.object.PlatformObject;
import com.ispf.driver.DeviceDriver;
import io.github.embeddedkafka.EmbeddedKafka$;
import io.github.embeddedkafka.EmbeddedKafkaConfig;
import io.github.embeddedkafka.EmbeddedKafkaConfig$;
import org.apache.kafka.clients.admin.AdminClient;
import org.apache.kafka.clients.admin.AdminClientConfig;
import org.apache.kafka.clients.admin.NewTopic;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.serialization.StringSerializer;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Properties;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class KafkaDeviceDriverTest {

    private static final String TOPIC = "ispf-test-events";
    private static EmbeddedKafkaConfig kafkaConfig;
    private static String bootstrapServers;

    private KafkaDeviceDriver driver;
    private StubDriverObject driverObject;

    @BeforeAll
    static void startKafka() throws Exception {
        kafkaConfig = EmbeddedKafkaConfig$.MODULE$.defaultConfig();
        EmbeddedKafka$.MODULE$.start(kafkaConfig);
        bootstrapServers = "localhost:" + kafkaConfig.kafkaPort();
        try (AdminClient admin = adminClient()) {
            admin.createTopics(java.util.List.of(new NewTopic(TOPIC, 1, (short) 1))).all().get(10, TimeUnit.SECONDS);
        }
    }

    @AfterAll
    static void stopKafka() {
        EmbeddedKafka$.MODULE$.stop();
    }

    @AfterEach
    void tearDown() {
        if (driver != null) {
            driver.disconnect();
        }
    }

    @Test
    void eventToVariableCreatesPerKeyVariables() throws Exception {
        driverObject = new StubDriverObject(Map.of(
                "bootstrapServers", bootstrapServers,
                "topic", TOPIC,
                "groupId", "ispf-test-" + UUID.randomUUID(),
                "eventToVariable", "true"
        ));
        driver = new KafkaDeviceDriver();
        driver.initialize(driverObject);
        driver.connect();
        driver.readPoints(Map.of("catchAll", "consume"));

        publish("site-a-temperature", "21.1");
        publish("site-b-temperature", "22.2");

        awaitVariable(driverObject, "site_a_temperature");
        awaitVariable(driverObject, "site_b_temperature");

        assertEquals("21.1", driverObject.variables.get("site_a_temperature").firstRow().get("raw"));
        assertEquals("22.2", driverObject.variables.get("site_b_temperature").firstRow().get("raw"));
        assertEquals("site-a-temperature", driverObject.variables.get("site_a_temperature").firstRow().get("key"));
    }

    @Test
    void eventToVariableDisabledUsesMappedVariable() throws Exception {
        driverObject = new StubDriverObject(Map.of(
                "bootstrapServers", bootstrapServers,
                "topic", TOPIC,
                "groupId", "ispf-test-" + UUID.randomUUID(),
                "eventToVariable", "false"
        ));
        driver = new KafkaDeviceDriver();
        driver.initialize(driverObject);
        driver.connect();

        publish("ignored-key", "99.0");
        driver.readPoints(Map.of("lastMessage", "consume"));

        assertEquals("99.0", driverObject.variables.get("lastMessage").firstRow().get("value"));
    }

    private void publish(String key, String payload) throws Exception {
        Properties props = new Properties();
        props.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        props.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class.getName());
        props.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, StringSerializer.class.getName());
        try (KafkaProducer<String, String> producer = new KafkaProducer<>(props)) {
            producer.send(new ProducerRecord<>(TOPIC, key, payload)).get(5, TimeUnit.SECONDS);
        }
    }

    private static AdminClient adminClient() {
        Properties props = new Properties();
        props.put(AdminClientConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        return AdminClient.create(props);
    }

    private static void awaitVariable(StubDriverObject driverObject, String name) throws InterruptedException {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(10);
        while (!driverObject.variables.containsKey(name) && System.nanoTime() < deadline) {
            Thread.sleep(50);
        }
        assertTrue(driverObject.variables.containsKey(name), "Timed out waiting for variable " + name);
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
                    "kafka-test",
                    "root.platform.devices.kafka-test",
                    ObjectType.DEVICE,
                    "Kafka Test",
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
