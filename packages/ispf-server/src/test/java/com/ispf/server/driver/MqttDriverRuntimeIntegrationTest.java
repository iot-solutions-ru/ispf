package com.ispf.server.driver;

import com.ispf.server.object.ObjectManager;
import com.ispf.server.plugin.blueprint.BlueprintApplicationService;
import io.moquette.broker.Server;
import io.moquette.broker.config.IConfig;
import io.moquette.broker.config.MemoryConfig;
import org.eclipse.paho.client.mqttv3.MqttClient;
import org.eclipse.paho.client.mqttv3.MqttMessage;
import org.eclipse.paho.client.mqttv3.persist.MemoryPersistence;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.net.ServerSocket;
import java.nio.charset.StandardCharsets;
import java.util.Properties;
import java.util.UUID;

import static org.hamcrest.Matchers.hasItem;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * BL-142: MQTT event→variable through DriverRuntimeService (not driver-only Moquette).
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class MqttDriverRuntimeIntegrationTest {

    private static final String DEVICE_NAME = "mqtt-etv-it";
    private static final String TOPIC_A = "ispf/devices/site-a/temperature";
    private static final String TOPIC_B = "ispf/devices/site-b/temperature";
    private static final String VAR_A = "ispf_devices_site_a_temperature";
    private static final String VAR_B = "ispf_devices_site_b_temperature";

    @Autowired
    private ObjectManager objectManager;

    @Autowired
    private BlueprintApplicationService BlueprintApplicationService;

    @Autowired
    private DriverRuntimeService driverRuntimeService;

    @Autowired
    private MockMvc mockMvc;

    private String devicePath;
    private Server broker;
    private int brokerPort;

    @BeforeEach
    void createDeviceAndBroker() throws Exception {
        devicePath = DriverIntegrationTestSupport.createDevice(
                objectManager,
                BlueprintApplicationService,
                driverRuntimeService,
                DEVICE_NAME
        );
        brokerPort = freePort();
        Properties props = new Properties();
        props.setProperty("host", "127.0.0.1");
        props.setProperty("port", String.valueOf(brokerPort));
        props.setProperty("allow_anonymous", "true");
        IConfig config = new MemoryConfig(props);
        broker = new Server();
        broker.startServer(config);
    }

    @AfterEach
    void cleanup() {
        DriverIntegrationTestSupport.deleteDevice(objectManager, driverRuntimeService, devicePath);
        if (broker != null) {
            broker.stopServer();
            broker = null;
        }
    }

    @Test
    void mqttEventToVariableCreatesPlatformVariables() throws Exception {
        mockMvc.perform(post("/api/v1/drivers/runtime/stop").param("devicePath", devicePath))
                .andExpect(status().isOk());

        mockMvc.perform(put("/api/v1/drivers/runtime/configure")
                        .param("devicePath", devicePath)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "driverId": "mqtt",
                                  "pollIntervalMs": 1000,
                                  "configuration": {
                                    "brokerUrl": "tcp://127.0.0.1:%d",
                                    "topicPrefix": "",
                                    "eventToVariable": "true"
                                  },
                                  "pointMappings": {
                                    "catchAll": "#"
                                  },
                                  "autoStart": true
                                }
                                """.formatted(brokerPort)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("RUNNING"))
                .andExpect(jsonPath("$.driverId").value("mqtt"));

        publish(TOPIC_A, "21.1");
        publish(TOPIC_B, "22.2");

        awaitVariableRaw(VAR_A, "21.1", 30_000);
        awaitVariableRaw(VAR_B, "22.2", 30_000);

        mockMvc.perform(get("/api/v1/objects/by-path/variables")
                        .param("path", devicePath))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[*].name", hasItem(VAR_A)))
                .andExpect(jsonPath("$[*].name", hasItem(VAR_B)));
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

    private void publish(String topic, String payload) throws Exception {
        try (MqttClient publisher = new MqttClient(
                "tcp://127.0.0.1:" + brokerPort,
                "publisher-" + UUID.randomUUID(),
                new MemoryPersistence()
        )) {
            publisher.connect();
            publisher.publish(topic, new MqttMessage(payload.getBytes(StandardCharsets.UTF_8)));
            publisher.disconnect();
        }
    }

    private static int freePort() throws Exception {
        try (ServerSocket socket = new ServerSocket(0)) {
            return socket.getLocalPort();
        }
    }
}
