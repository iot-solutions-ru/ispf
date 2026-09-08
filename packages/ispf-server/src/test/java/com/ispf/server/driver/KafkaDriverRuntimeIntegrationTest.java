package com.ispf.server.driver;

import com.ispf.server.object.ObjectManager;
import com.ispf.server.plugin.blueprint.BlueprintApplicationService;
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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Properties;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import static org.hamcrest.Matchers.hasItem;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * BL-142: Kafka event→variable through DriverRuntimeService (not driver-only EmbeddedKafka).
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class KafkaDriverRuntimeIntegrationTest {

    private static final String DEVICE_NAME = "kafka-etv-it";
    private static final String TOPIC = "ispf-server-kafka-etv";

    private static EmbeddedKafkaConfig kafkaConfig;
    private static String bootstrapServers;

    @Autowired
    private ObjectManager objectManager;

    @Autowired
    private BlueprintApplicationService BlueprintApplicationService;

    @Autowired
    private DriverRuntimeService driverRuntimeService;

    @Autowired
    private MockMvc mockMvc;

    private String devicePath;

    @BeforeAll
    static void startKafka() throws Exception {
        kafkaConfig = EmbeddedKafkaConfig$.MODULE$.defaultConfig();
        EmbeddedKafka$.MODULE$.start(kafkaConfig);
        bootstrapServers = "localhost:" + kafkaConfig.kafkaPort();
        try (AdminClient admin = adminClient()) {
            admin.createTopics(java.util.List.of(new NewTopic(TOPIC, 1, (short) 1))).all().get(15, TimeUnit.SECONDS);
        }
    }

    @AfterAll
    static void stopKafka() {
        EmbeddedKafka$.MODULE$.stop();
    }

    @BeforeEach
    void createDevice() {
        devicePath = DriverIntegrationTestSupport.createDevice(
                objectManager,
                BlueprintApplicationService,
                driverRuntimeService,
                DEVICE_NAME
        );
    }

    @AfterEach
    void cleanup() {
        DriverIntegrationTestSupport.deleteDevice(objectManager, driverRuntimeService, devicePath);
    }

    @Test
    void kafkaEventToVariableCreatesPlatformVariables() throws Exception {
        mockMvc.perform(post("/api/v1/drivers/runtime/stop").param("devicePath", devicePath))
                .andExpect(status().isOk());

        mockMvc.perform(put("/api/v1/drivers/runtime/configure")
                        .param("devicePath", devicePath)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "driverId": "kafka",
                                  "pollIntervalMs": 1000,
                                  "configuration": {
                                    "bootstrapServers": "%s",
                                    "topic": "%s",
                                    "groupId": "ispf-server-it-%s",
                                    "eventToVariable": "true"
                                  },
                                  "pointMappings": {
                                    "catchAll": "consume"
                                  },
                                  "autoStart": true
                                }
                                """.formatted(bootstrapServers, TOPIC, UUID.randomUUID())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("RUNNING"))
                .andExpect(jsonPath("$.driverId").value("kafka"));

        publish("site-a-temperature", "21.1");
        publish("site-b-temperature", "22.2");

        awaitVariableRaw("site_a_temperature", "21.1", 30_000);
        awaitVariableRaw("site_b_temperature", "22.2", 30_000);

        mockMvc.perform(get("/api/v1/objects/by-path/variables")
                        .param("path", devicePath))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[*].name", hasItem("site_a_temperature")))
                .andExpect(jsonPath("$[*].name", hasItem("site_b_temperature")));
    }

    private void awaitVariableRaw(String name, String expectedRaw, long timeoutMs) throws Exception {
        long deadline = System.currentTimeMillis() + timeoutMs;
        String lastBody = "";
        int lastStatus = -1;
        while (System.currentTimeMillis() < deadline) {
            mockMvc.perform(post("/api/v1/drivers/runtime/poll").param("devicePath", devicePath))
                    .andExpect(status().isOk());
            var result = mockMvc.perform(get("/api/v1/objects/by-path/variables/detail")
                            .param("path", devicePath)
                            .param("name", name))
                    .andReturn();
            lastStatus = result.getResponse().getStatus();
            lastBody = result.getResponse().getContentAsString();
            if (lastStatus == 200 && lastBody.contains(expectedRaw)) {
                return;
            }
            Thread.sleep(250);
        }
        throw new AssertionError(
                "Timed out waiting for variable " + name + " raw=" + expectedRaw
                        + " status=" + lastStatus + " last=" + lastBody);
    }

    private void publish(String key, String payload) throws Exception {
        Properties props = new Properties();
        props.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        props.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class.getName());
        props.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, StringSerializer.class.getName());
        try (KafkaProducer<String, String> producer = new KafkaProducer<>(props)) {
            producer.send(new ProducerRecord<>(TOPIC, key, payload)).get(10, TimeUnit.SECONDS);
        }
    }

    private static AdminClient adminClient() {
        Properties props = new Properties();
        props.put(AdminClientConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        return AdminClient.create(props);
    }
}
