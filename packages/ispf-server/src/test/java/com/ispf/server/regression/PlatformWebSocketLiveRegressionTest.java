package com.ispf.server.regression;

import com.ispf.core.model.DataRecord;
import com.ispf.core.model.DataSchema;
import com.ispf.core.model.FieldType;
import com.ispf.core.object.ObjectType;
import com.ispf.server.object.ObjectManager;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.client.standard.StandardWebSocketClient;
import org.springframework.web.socket.handler.TextWebSocketHandler;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.net.URI;
import java.util.Map;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * End-to-end live WebSocket regression test over {@code /ws/objects} verifying:
 *
 * <ul>
 *   <li>Unsubscribed sessions receive zero broadcast traffic (demand-driven contract)</li>
 *   <li>Path-wide {@code subscribe} delivers {@code VARIABLE_UPDATED} frames with {@code path},
 *       {@code variableName}, and {@code revision}</li>
 *   <li>Variable-filtered {@code variablesByPath} subscription delivers updates only for the
 *       requested variable and drops unsubscribed variables on the same object</li>
 *   <li>{@code presence} heartbeat and {@code subscribe_events} validation frames round-trip</li>
 * </ul>
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class PlatformWebSocketLiveRegressionTest {

    private static final String WS_SENSOR = "root.platform.devices.ws-reg-sensor-01";
    private static final String WS_OTHER = "root.platform.devices.ws-reg-other-01";
    private static final DataSchema DOUBLE_SCHEMA = DataSchema.builder("doubleValue")
            .field("value", FieldType.DOUBLE)
            .build();

    @LocalServerPort
    private int port;

    @Autowired
    private ObjectManager objectManager;

    @Autowired
    private ObjectMapper objectMapper;

    @BeforeEach
    void setupNodes() {
        ensureSensorWithVariables(WS_SENSOR, "ws-reg-sensor-01");
        ensureSensorWithVariables(WS_OTHER, "ws-reg-other-01");
    }

    @AfterEach
    void cleanupNodes() {
        if (objectManager.tree().findByPath(WS_SENSOR).isPresent()) {
            objectManager.delete(WS_SENSOR);
        }
        if (objectManager.tree().findByPath(WS_OTHER).isPresent()) {
            objectManager.delete(WS_OTHER);
        }
    }

    @Test
    void webSocketSubscriptionFilteringPresenceAndEventCatalogContract() throws Exception {
        BlockingQueue<JsonNode> inbox = new LinkedBlockingQueue<>();
        StandardWebSocketClient client = new StandardWebSocketClient();
        URI uri = URI.create("ws://127.0.0.1:" + port + "/ws/objects");

        try (WebSocketSession session = client.execute(new TextWebSocketHandler() {
            @Override
            protected void handleTextMessage(WebSocketSession ws, TextMessage message) {
                inbox.offer(objectMapper.readTree(message.getPayload()));
            }
        }, uri.toString()).get(10, TimeUnit.SECONDS)) {

            // 1. Before subscribing: mutations on objects produce no broadcast noise
            objectManager.setVariableValue(
                    WS_OTHER,
                    "temperature",
                    DataRecord.single(DOUBLE_SCHEMA, Map.of("value", 11.0))
            );
            assertThat(inbox.poll(250, TimeUnit.MILLISECONDS)).isNull();

            // 2. Variable-filtered subscription: watch ONLY "temperature" on WS_SENSOR
            session.sendMessage(new TextMessage("""
                    {
                      "type": "subscribe",
                      "paths": ["%s"],
                      "variablesByPath": {
                        "%s": ["temperature"]
                      }
                    }
                    """.formatted(WS_SENSOR, WS_SENSOR)));
            // Allow server inbound handler to register subscription interest before mutation
            session.sendMessage(new TextMessage("""
                    {
                      "type": "presence",
                      "path": "%s",
                      "username": "ws-tester",
                      "mode": "edit"
                    }
                    """.formatted(WS_SENSOR)));

            JsonNode presenceMsg = awaitFrameOfType(inbox, "presence");
            assertThat(presenceMsg.path("path").asString()).isEqualTo(WS_SENSOR);
            assertThat(presenceMsg.path("entries").get(0).path("username").asString()).isEqualTo("ws-tester");

            // Unsubscribed variable ("pressure") on WS_SENSOR must NOT be delivered
            objectManager.setVariableValue(
                    WS_SENSOR,
                    "pressure",
                    DataRecord.single(DOUBLE_SCHEMA, Map.of("value", 4.2))
            );
            assertThat(inbox.poll(250, TimeUnit.MILLISECONDS)).isNull();

            // Subscribed variable ("temperature") on WS_SENSOR MUST arrive with revision & value
            objectManager.setVariableValue(
                    WS_SENSOR,
                    "temperature",
                    DataRecord.single(DOUBLE_SCHEMA, Map.of("value", 77.5))
            );
            JsonNode updateMsg = awaitFrameOfType(inbox, "VARIABLE_UPDATED");
            assertThat(updateMsg.path("path").asString()).isEqualTo(WS_SENSOR);
            assertThat(updateMsg.path("variableName").asString()).isEqualTo("temperature");
            assertThat(updateMsg.has("revision")).isTrue();

            // 3. Path-wide subscription (without variablesByPath) delivers any variable on WS_OTHER
            session.sendMessage(new TextMessage("""
                    {
                      "type": "subscribe",
                      "paths": ["%s"]
                    }
                    """.formatted(WS_OTHER)));
            session.sendMessage(new TextMessage("""
                    {
                      "type": "presence",
                      "path": "%s",
                      "username": "ws-tester",
                      "mode": "view"
                    }
                    """.formatted(WS_OTHER)));
            awaitFrameOfType(inbox, "presence");

            objectManager.setVariableValue(
                    WS_OTHER,
                    "pressure",
                    DataRecord.single(DOUBLE_SCHEMA, Map.of("value", 6.8))
            );
            JsonNode pathWideMsg = awaitFrameOfType(inbox, "VARIABLE_UPDATED");
            assertThat(pathWideMsg.path("path").asString()).isEqualTo(WS_OTHER);
            assertThat(pathWideMsg.path("variableName").asString()).isEqualTo("pressure");

            // 4. subscribe_events without appId returns APP_ID_REQUIRED rejection frame
            session.sendMessage(new TextMessage("""
                    {
                      "type": "subscribe_events",
                      "events": ["cellOverheated"]
                    }
                    """));
            JsonNode subEventsErr = awaitFrameOfType(inbox, "subscribe_events_result");
            assertThat(subEventsErr.path("rejected").get(0).path("reason").asString())
                    .isEqualTo("APP_ID_REQUIRED");
        }
    }

    private JsonNode awaitFrameOfType(BlockingQueue<JsonNode> inbox, String expectedType) throws InterruptedException {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
        while (System.nanoTime() < deadline) {
            long remainingMs = TimeUnit.NANOSECONDS.toMillis(deadline - System.nanoTime());
            JsonNode frame = inbox.poll(Math.max(50L, remainingMs), TimeUnit.MILLISECONDS);
            if (frame != null && expectedType.equals(frame.path("type").asString())) {
                return frame;
            }
        }
        throw new AssertionError("Timed out waiting for WebSocket frame of type: " + expectedType);
    }

    private void ensureSensorWithVariables(String path, String name) {
        if (objectManager.tree().findByPath(path).isEmpty()) {
            objectManager.create("root.platform.devices", name, ObjectType.DEVICE, name, "ws test sensor", null);
        }
        if (objectManager.require(path).getVariable("temperature").isEmpty()) {
            objectManager.createVariable(
                    path,
                    "temperature",
                    DOUBLE_SCHEMA,
                    true,
                    true,
                    DataRecord.single(DOUBLE_SCHEMA, Map.of("value", 20.0)),
                    false,
                    null
            );
        }
        if (objectManager.require(path).getVariable("pressure").isEmpty()) {
            objectManager.createVariable(
                    path,
                    "pressure",
                    DOUBLE_SCHEMA,
                    true,
                    true,
                    DataRecord.single(DOUBLE_SCHEMA, Map.of("value", 1.0)),
                    false,
                    null
            );
        }
        objectManager.persistNodeTree(path);
    }
}
