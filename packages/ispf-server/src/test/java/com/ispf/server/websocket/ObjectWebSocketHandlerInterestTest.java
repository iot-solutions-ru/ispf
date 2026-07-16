package com.ispf.server.websocket;

import com.ispf.server.application.catalog.ApplicationEventCatalogService;
import com.ispf.server.config.WebSocketProperties;
import com.ispf.server.federation.FederationSubscribePollService;
import com.ispf.server.object.ObjectChangeEvent;
import com.ispf.server.object.ObjectManager;
import com.ispf.server.object.pubsub.NoOpClusterPathInterestStore;
import com.ispf.server.object.pubsub.ObjectWebSocketPathInterestRegistry;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import tools.jackson.databind.ObjectMapper;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class ObjectWebSocketHandlerInterestTest {

    private ObjectWebSocketHandler handler;
    private ObjectWebSocketPathInterestRegistry pathInterestRegistry;

    @BeforeEach
    void setUp() {
        WebSocketProperties properties = new WebSocketProperties();
        properties.setSendElasticEnabled(false);
        properties.setSendThreads(1);
        pathInterestRegistry = new ObjectWebSocketPathInterestRegistry();
        handler = new ObjectWebSocketHandler(
                properties,
                new ObjectMapper(),
                mock(FederationSubscribePollService.class),
                mock(ObjectPresenceService.class),
                mock(ObjectManager.class),
                mock(ApplicationEventCatalogService.class),
                pathInterestRegistry,
                new NoOpClusterPathInterestStore()
        );
        handler.startSendWorkers();
    }

    @AfterEach
    void tearDown() {
        handler.shutdownSendWorkers();
    }

    @Test
    void emptyInterestMeansSilence() throws Exception {
        RecordingSession session = openSession("s-silent");
        handler.onObjectChange(ObjectChangeEvent.variableUpdated("root.devices.pump", "speed"));
        assertThat(awaitMessages(session, 0, 200)).isEmpty();
        assertThat(pathInterestRegistry.hasPathInterest("root.devices.pump")).isFalse();
    }

    @Test
    void subscribeVariablesByPathDeliversOnlyMatchingVariable() throws Exception {
        RecordingSession interested = openSession("s-interested");
        RecordingSession otherVar = openSession("s-other");

        interested.sendSubscribe("""
                {"type":"subscribe","paths":["root.devices.pump"],"variablesByPath":{"root.devices.pump":["speed"]}}
                """);
        otherVar.sendSubscribe("""
                {"type":"subscribe","paths":["root.devices.pump"],"variablesByPath":{"root.devices.pump":["temp"]}}
                """);

        handler.onObjectChange(ObjectChangeEvent.variableUpdated("root.devices.pump", "speed"));

        assertThat(awaitMessages(interested, 1, 2000)).hasSize(1);
        assertThat(interested.messages.getFirst()).contains("\"variableName\":\"speed\"");
        assertThat(awaitMessages(otherVar, 0, 200)).isEmpty();
        assertThat(pathInterestRegistry.hasVariableInterest("root.devices.pump", "speed")).isTrue();
        assertThat(pathInterestRegistry.hasVariableInterest("root.devices.pump", "temp")).isTrue();
    }

    @Test
    void pathWideSubscribeReceivesAnyVariableOnPath() throws Exception {
        RecordingSession session = openSession("s-wide");
        session.sendSubscribe("""
                {"type":"subscribe","paths":["root.devices.pump"]}
                """);

        handler.onObjectChange(ObjectChangeEvent.variableUpdated("root.devices.pump", "speed"));

        assertThat(awaitMessages(session, 1, 2000)).hasSize(1);
        assertThat(session.messages.getFirst()).contains("VARIABLE_UPDATED");
    }

    private RecordingSession openSession(String id) throws Exception {
        RecordingSession session = new RecordingSession(id);
        handler.afterConnectionEstablished(session);
        return session;
    }

    private static List<String> awaitMessages(RecordingSession session, int minSize, long timeoutMs)
            throws InterruptedException {
        long deadline = System.nanoTime() + TimeUnitMs.toNanos(timeoutMs);
        while (session.messages.size() < minSize && System.nanoTime() < deadline) {
            Thread.sleep(20);
        }
        return List.copyOf(session.messages);
    }

    private static final class TimeUnitMs {
        static long toNanos(long ms) {
            return ms * 1_000_000L;
        }
    }

    private final class RecordingSession implements WebSocketSession {
        private final String id;
        private final Map<String, Object> attributes = new HashMap<>();
        private final List<String> messages = new CopyOnWriteArrayList<>();
        private boolean open = true;

        private RecordingSession(String id) {
            this.id = id;
        }

        void sendSubscribe(String json) throws Exception {
            handler.handleTextMessage(this, new TextMessage(json));
        }

        @Override
        public String getId() {
            return id;
        }

        @Override
        public boolean isOpen() {
            return open;
        }

        @Override
        public void sendMessage(org.springframework.web.socket.WebSocketMessage<?> message) {
            if (message instanceof TextMessage text) {
                messages.add(text.getPayload());
            }
        }

        @Override
        public Map<String, Object> getAttributes() {
            return attributes;
        }

        @Override public java.net.URI getUri() { return null; }
        @Override public org.springframework.http.HttpHeaders getHandshakeHeaders() {
            return new org.springframework.http.HttpHeaders();
        }
        @Override public java.security.Principal getPrincipal() { return null; }
        @Override public java.net.InetSocketAddress getLocalAddress() { return null; }
        @Override public java.net.InetSocketAddress getRemoteAddress() { return null; }
        @Override public String getAcceptedProtocol() { return null; }
        @Override public void setTextMessageSizeLimit(int messageSizeLimit) { }
        @Override public int getTextMessageSizeLimit() { return 0; }
        @Override public void setBinaryMessageSizeLimit(int messageSizeLimit) { }
        @Override public int getBinaryMessageSizeLimit() { return 0; }
        @Override public List<org.springframework.web.socket.WebSocketExtension> getExtensions() {
            return List.of();
        }
        @Override public void close() { open = false; }
        @Override public void close(org.springframework.web.socket.CloseStatus status) { open = false; }
    }
}
