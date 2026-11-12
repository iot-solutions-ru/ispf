package com.ispf.server.websocket;

import com.ispf.server.application.catalog.ApplicationEventCatalogService;
import com.ispf.server.config.WebSocketProperties;
import com.ispf.server.federation.FederationSubscribePollService;
import com.ispf.server.object.ObjectChangeEvent;
import com.ispf.server.object.ObjectChangeType;
import com.ispf.server.object.ObjectManager;
import com.ispf.server.object.pubsub.NoOpClusterPathInterestStore;
import com.ispf.server.object.pubsub.ObjectWebSocketPathInterestRegistry;
import com.ispf.server.security.acl.ObjectAccessService;
import com.ispf.server.security.acl.VariableMemberAccessService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import tools.jackson.databind.ObjectMapper;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ObjectWebSocketHandlerInterestTest {

    private ObjectWebSocketHandler handler;
    private ObjectWebSocketPathInterestRegistry pathInterestRegistry;
    private ObjectAccessService objectAccessService;
    private VariableMemberAccessService variableMemberAccessService;

    @BeforeEach
    void setUp() {
        WebSocketProperties properties = new WebSocketProperties();
        properties.setSendElasticEnabled(false);
        properties.setSendThreads(1);
        pathInterestRegistry = new ObjectWebSocketPathInterestRegistry();
        objectAccessService = mock(ObjectAccessService.class);
        variableMemberAccessService = mock(VariableMemberAccessService.class);
        handler = new ObjectWebSocketHandler(
                properties,
                new ObjectMapper(),
                mock(FederationSubscribePollService.class),
                mock(ObjectPresenceService.class),
                mock(ObjectManager.class),
                mock(ApplicationEventCatalogService.class),
                pathInterestRegistry,
                new NoOpClusterPathInterestStore(),
                objectAccessService,
                variableMemberAccessService
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

    @Test
    void subscribeDropsPathsWithoutReadPermission() throws Exception {
        RecordingSession session = openSession("s-acl");
        session.attributes.put(
                WebSocketAuthHandshakeInterceptor.AUTHENTICATION_ATTRIBUTE,
                authentication("operator")
        );
        when(objectAccessService.canRead(eq("root.devices.pump"), any())).thenReturn(true);
        when(objectAccessService.canRead(eq("root.platform.security"), any())).thenReturn(false);

        session.sendSubscribe("""
                {"type":"subscribe","paths":["root.devices.pump","root.platform.security"]}
                """);

        assertThat(pathInterestRegistry.hasPathInterest("root.devices.pump")).isTrue();
        assertThat(pathInterestRegistry.hasPathInterest("root.platform.security")).isFalse();

        handler.onObjectChange(ObjectChangeEvent.variableUpdated("root.platform.security", "users"));
        assertThat(awaitMessages(session, 0, 200)).isEmpty();
    }

    @Test
    void ancestorSubscriptionDoesNotLeakUnreadableDescendantEvents() throws Exception {
        RecordingSession session = openSession("s-ancestor");
        session.attributes.put(
                WebSocketAuthHandshakeInterceptor.AUTHENTICATION_ATTRIBUTE,
                authentication("operator")
        );
        when(objectAccessService.canRead(any(), any())).thenReturn(true);
        when(objectAccessService.canRead(eq("root.platform.security.users.admin"), any())).thenReturn(false);
        when(variableMemberAccessService.canRead(any(), any(), any())).thenReturn(true);

        session.sendSubscribe("""
                {"type":"subscribe","paths":["root.platform"]}
                """);

        handler.onObjectChange(ObjectChangeEvent.variableUpdated("root.platform.security.users.admin", "displayName"));
        assertThat(awaitMessages(session, 0, 200)).isEmpty();

        handler.onObjectChange(ObjectChangeEvent.variableUpdated("root.platform.devices.pump", "speed"));
        assertThat(awaitMessages(session, 1, 2000)).hasSize(1);
    }

    @Test
    void subscribeVariablesByPathDropsRestrictedVariables() throws Exception {
        RecordingSession session = openSession("s-variable-acl");
        session.attributes.put(
                WebSocketAuthHandshakeInterceptor.AUTHENTICATION_ATTRIBUTE,
                authentication("operator")
        );
        when(objectAccessService.canRead(eq("root.devices.pump"), any())).thenReturn(true);
        when(variableMemberAccessService.canRead(eq("root.devices.pump"), eq("speed"), any()))
                .thenReturn(true);
        when(variableMemberAccessService.canRead(eq("root.devices.pump"), eq("secret"), any()))
                .thenReturn(false);

        session.sendSubscribe("""
                {"type":"subscribe","paths":["root.devices.pump"],"variablesByPath":{"root.devices.pump":["speed","secret"]}}
                """);

        assertThat(pathInterestRegistry.hasVariableInterest("root.devices.pump", "speed")).isTrue();
        assertThat(pathInterestRegistry.hasVariableInterest("root.devices.pump", "secret")).isFalse();

        handler.onObjectChange(ObjectChangeEvent.variableUpdated("root.devices.pump", "secret"));
        assertThat(awaitMessages(session, 0, 200)).isEmpty();

        handler.onObjectChange(ObjectChangeEvent.variableUpdated("root.devices.pump", "speed"));
        assertThat(awaitMessages(session, 1, 2000)).hasSize(1);
    }

    @Test
    void pathWideSubscriptionFiltersRestrictedVariablesAtDelivery() throws Exception {
        RecordingSession session = openSession("s-wide-variable-acl");
        session.attributes.put(
                WebSocketAuthHandshakeInterceptor.AUTHENTICATION_ATTRIBUTE,
                authentication("operator")
        );
        when(objectAccessService.canRead(eq("root.devices.pump"), any())).thenReturn(true);
        when(variableMemberAccessService.canRead(eq("root.devices.pump"), eq("secret"), any()))
                .thenReturn(false);
        when(variableMemberAccessService.canRead(eq("root.devices.pump"), eq("speed"), any()))
                .thenReturn(true);

        session.sendSubscribe("""
                {"type":"subscribe","paths":["root.devices.pump"]}
                """);

        handler.onObjectChange(ObjectChangeEvent.variableUpdated("root.devices.pump", "secret"));
        assertThat(awaitMessages(session, 0, 200)).isEmpty();

        handler.onObjectChange(ObjectChangeEvent.variableUpdated("root.devices.pump", "speed"));
        assertThat(awaitMessages(session, 1, 2000)).hasSize(1);
        assertThat(session.messages.getFirst()).contains("\"variableName\":\"speed\"");
    }

    @Test
    void failedSendIsRetriedThenVisibleOnSession() throws Exception {
        RecordingSession session = openSession("s-send-fail");
        session.failSends = true;
        session.sendSubscribe("""
                {"type":"subscribe","paths":["root.devices.pump"]}
                """);

        handler.onObjectChange(ObjectChangeEvent.variableUpdated("root.devices.pump", "speed"));

        long deadline = System.nanoTime() + 2_000_000_000L;
        while (handler.sendFailureCount() < 1 && System.nanoTime() < deadline) {
            Thread.sleep(20);
        }
        assertThat(handler.sendFailureCount()).isEqualTo(1);
        assertThat(session.sendAttempts).isEqualTo(2);
        assertThat(session.messages).isEmpty();
        assertThat(handler.sessionDeliveryErrors())
                .anySatisfy(row -> {
                    assertThat(row.get("sessionId")).isEqualTo("s-send-fail");
                    assertThat(String.valueOf(row.get("error"))).contains("send failed");
                });
    }

    @Test
    void sendQueueDropsPastCapacityAndReleasesPayloadOnClose() throws Exception {
        WebSocketProperties properties = new WebSocketProperties();
        properties.setSendElasticEnabled(false);
        properties.setSendThreads(1);
        properties.setPendingSendMax(2);
        ObjectWebSocketHandler capped = new ObjectWebSocketHandler(
                properties,
                new ObjectMapper(),
                mock(FederationSubscribePollService.class),
                mock(ObjectPresenceService.class),
                mock(ObjectManager.class),
                mock(ApplicationEventCatalogService.class),
                new ObjectWebSocketPathInterestRegistry(),
                new NoOpClusterPathInterestStore(),
                objectAccessService,
                variableMemberAccessService
        );
        capped.startSendWorkers();
        BlockingSession session = new BlockingSession("s-cap");
        try {
            capped.afterConnectionEstablished(session);
            capped.handleTextMessage(session, new TextMessage(
                    "{\"type\":\"subscribe\",\"paths\":[\"root.devices.queuecap\"]}"
            ));
            capped.onObjectChange(ObjectChangeEvent.variableUpdated("root.devices.queuecap", "speed"));
            assertThat(session.entered.await(2, TimeUnit.SECONDS)).isTrue();
            for (int i = 0; i < 20; i++) {
                capped.onObjectChange(ObjectChangeEvent.variableUpdated("root.devices.queuecap", "speed"));
                assertThat(capped.pendingSendDepth()).isLessThanOrEqualTo(2);
            }
            assertThat(capped.droppedSendCount()).isGreaterThan(0);
            capped.afterConnectionClosed(session, CloseStatus.SERVICE_OVERLOAD);
            assertThat(capped.pendingSendDepth()).isZero();
        } finally {
            session.release.countDown();
            capped.shutdownSendWorkers();
        }
    }

    @Test
    void slowSessionOverflowDoesNotCloseHealthyPeer() throws Exception {
        WebSocketProperties properties = new WebSocketProperties();
        properties.setSendElasticEnabled(false);
        properties.setSendThreads(1);
        properties.setPendingSendMax(2);
        ObjectWebSocketHandler capped = new ObjectWebSocketHandler(
                properties,
                new ObjectMapper(),
                mock(FederationSubscribePollService.class),
                mock(ObjectPresenceService.class),
                mock(ObjectManager.class),
                mock(ApplicationEventCatalogService.class),
                new ObjectWebSocketPathInterestRegistry(),
                new NoOpClusterPathInterestStore(),
                objectAccessService,
                variableMemberAccessService
        );
        capped.startSendWorkers();
        BlockingSession slow = new BlockingSession("s-slow");
        RecordingSession healthy = new RecordingSession("s-healthy");
        try {
            capped.afterConnectionEstablished(slow);
            capped.afterConnectionEstablished(healthy);
            capped.handleTextMessage(slow, new TextMessage(
                    "{\"type\":\"subscribe\",\"paths\":[\"root.devices.slow\"]}"
            ));
            capped.handleTextMessage(healthy, new TextMessage(
                    "{\"type\":\"subscribe\",\"paths\":[\"root.devices.healthy\"]}"
            ));

            capped.onObjectChange(ObjectChangeEvent.variableUpdated("root.devices.slow", "speed"));
            assertThat(slow.entered.await(2, TimeUnit.SECONDS)).isTrue();
            for (int i = 0; i < 20; i++) {
                capped.onObjectChange(ObjectChangeEvent.variableUpdated("root.devices.slow", "speed"));
            }
            assertThat(slow.isOpen()).isFalse();
            assertThat(healthy.isOpen()).isTrue();
            assertThat(capped.droppedSendCount()).isGreaterThan(0);
            assertThat(capped.pendingSendDepth()).isLessThanOrEqualTo(2);

            slow.release.countDown();
            capped.onObjectChange(ObjectChangeEvent.variableUpdated("root.devices.healthy", "speed"));
            assertThat(awaitMessages(healthy, 1, 2000)).hasSize(1);
        } finally {
            slow.release.countDown();
            capped.shutdownSendWorkers();
        }
    }

    @Test
    void subscribeWithLastSeqReplaysOnlyNewerFrames() throws Exception {
        RecordingSession first = openSession("s-replay-first");
        first.sendSubscribe("""
                {"type":"subscribe","paths":["root.devices.pump"]}
                """);
        handler.onObjectChange(ObjectChangeEvent.variableUpdated("root.devices.pump", "speed"));
        handler.onObjectChange(ObjectChangeEvent.variableUpdated("root.devices.pump", "temp"));
        List<String> live = awaitMessages(first, 2, 2000);
        assertThat(live).hasSize(2);
        long seen = seqOf(live.getFirst());

        RecordingSession again = openSession("s-replay-again");
        again.sendSubscribe("""
                {"type":"subscribe","paths":["root.devices.pump"],"lastSeq":%d}
                """.formatted(seen));

        List<String> replayed = awaitMessages(again, 1, 2000);
        assertThat(replayed).hasSize(1);
        assertThat(replayed.getFirst()).contains("\"variableName\":\"temp\"");
        assertThat(replayed.getFirst()).contains("\"seq\":" + seqOf(live.get(1)));
    }

    @Test
    void subscribeWithExpiredLastSeqReportsGap() throws Exception {
        handler.resizeReplayBufferForTest(1);
        RecordingSession first = openSession("s-gap-first");
        first.sendSubscribe("""
                {"type":"subscribe","paths":["root.devices.pump"]}
                """);
        handler.onObjectChange(ObjectChangeEvent.variableUpdated("root.devices.pump", "speed"));
        handler.onObjectChange(ObjectChangeEvent.variableUpdated("root.devices.pump", "temp"));
        handler.onObjectChange(ObjectChangeEvent.variableUpdated("root.devices.pump", "flow"));
        List<String> live = awaitMessages(first, 3, 2000);
        assertThat(live).hasSize(3);

        RecordingSession again = openSession("s-gap-again");
        again.sendSubscribe("""
                {"type":"subscribe","paths":["root.devices.pump"],"lastSeq":%d}
                """.formatted(seqOf(live.getFirst())));

        List<String> replayed = awaitMessages(again, 1, 2000);
        assertThat(replayed).hasSize(1);
        assertThat(replayed.getFirst()).contains("\"type\":\"replay_gap\"");
    }

    @Test
    void replaySkipsFramesOutsideTheSubscription() throws Exception {
        handler.onObjectChange(ObjectChangeEvent.of(ObjectChangeType.CREATED, "root.devices.other"));
        handler.onObjectChange(ObjectChangeEvent.variableUpdated("root.devices.pump", "speed"));

        RecordingSession session = openSession("s-replay-filter");
        session.sendSubscribe("""
                {"type":"subscribe","paths":["root.devices.pump"],"variablesByPath":{"root.devices.pump":["speed"]},"lastSeq":0}
                """);

        List<String> replayed = awaitMessages(session, 1, 2000);
        assertThat(replayed).hasSize(1);
        assertThat(replayed.getFirst()).contains("\"variableName\":\"speed\"");
        assertThat(replayed.getFirst()).doesNotContain("root.devices.other");
    }

    @Test
    void replayReportsGapWhenCursorIsAheadOfThisServer() throws Exception {
        RecordingSession session = openSession("s-restart");
        session.sendSubscribe("""
                {"type":"subscribe","paths":["root.devices.pump"],"lastSeq":50}
                """);

        List<String> replayed = awaitMessages(session, 1, 2000);
        assertThat(replayed).hasSize(1);
        assertThat(replayed.getFirst()).contains("\"type\":\"replay_gap\"");
        assertThat(replayed.getFirst()).contains("\"lastSeq\":0");
    }

    private static long seqOf(String json) {
        java.util.regex.Matcher matcher = java.util.regex.Pattern.compile("\"seq\":(\\d+)").matcher(json);
        assertThat(matcher.find()).isTrue();
        return Long.parseLong(matcher.group(1));
    }

    private static UsernamePasswordAuthenticationToken authentication(String role) {
        return new UsernamePasswordAuthenticationToken(
                "ws-user",
                null,
                List.of(new SimpleGrantedAuthority("ROLE_" + role))
        );
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
        private boolean failSends;
        private int sendAttempts;

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
        public void sendMessage(org.springframework.web.socket.WebSocketMessage<?> message) throws java.io.IOException {
            if (failSends) {
                sendAttempts++;
                throw new java.io.IOException("send failed");
            }
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

    private static final class BlockingSession implements WebSocketSession {
        private final String id;
        private final Map<String, Object> attributes = new HashMap<>();
        private final CountDownLatch entered = new CountDownLatch(1);
        private final CountDownLatch release = new CountDownLatch(1);
        private boolean open = true;

        private BlockingSession(String id) {
            this.id = id;
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
        public void sendMessage(org.springframework.web.socket.WebSocketMessage<?> message) throws java.io.IOException {
            entered.countDown();
            try {
                if (!release.await(5, TimeUnit.SECONDS)) {
                    throw new java.io.IOException("send blocked");
                }
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
                throw new java.io.IOException("send interrupted", ex);
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
