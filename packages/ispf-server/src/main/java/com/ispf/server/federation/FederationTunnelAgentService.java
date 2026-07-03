package com.ispf.server.federation;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import com.ispf.server.object.ObjectChangeEvent;
import com.ispf.server.object.ObjectChangeType;
import com.ispf.server.security.IspfSecretCipher;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.WebSocket;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

@Service
public class FederationTunnelAgentService {

    private static final Logger log = LoggerFactory.getLogger(FederationTunnelAgentService.class);

    private final FederationOutboundAgentStore agentStore;
    private final IspfSecretCipher secretCipher;
    private final FederationTunnelLocalProxyService localProxyService;
    private final FederationOutboundEventBufferRegistry eventBufferRegistry;
    private final ObjectMapper objectMapper;
    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(15))
            .build();
    private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
        Thread thread = new Thread(r, "federation-tunnel-agent");
        thread.setDaemon(true);
        return thread;
    });
    private final ExecutorService proxyExecutor = Executors.newCachedThreadPool(r -> {
        Thread thread = new Thread(r, "federation-tunnel-proxy");
        thread.setDaemon(true);
        return thread;
    });
    private final Map<UUID, AgentRuntime> runtimes = new ConcurrentHashMap<>();
    private final Map<UUID, Object> connectLocks = new ConcurrentHashMap<>();

    public FederationTunnelAgentService(
            FederationOutboundAgentStore agentStore,
            IspfSecretCipher secretCipher,
            FederationTunnelLocalProxyService localProxyService,
            FederationOutboundEventBufferRegistry eventBufferRegistry,
            ObjectMapper objectMapper
    ) {
        this.agentStore = agentStore;
        this.secretCipher = secretCipher;
        this.localProxyService = localProxyService;
        this.eventBufferRegistry = eventBufferRegistry;
        this.objectMapper = objectMapper;
    }

    @EventListener(ApplicationReadyEvent.class)
    @Order(org.springframework.core.Ordered.LOWEST_PRECEDENCE)
    public void connectEnabledAgents() {
        for (FederationOutboundAgent agent : agentStore.listEnabled()) {
            scheduleConnect(agent.id());
        }
    }

    public void scheduleConnect(UUID agentId) {
        scheduler.execute(() -> connectNow(agentId));
    }

    public void connectNow(UUID agentId) {
        Object lock = connectLocks.computeIfAbsent(agentId, ignored -> new Object());
        synchronized (lock) {
            FederationOutboundAgent agent = agentStore.findById(agentId).orElse(null);
            if (agent == null || !agent.enabled()) {
                return;
            }
            disconnectRuntime(agentId);
            agentStore.updateStatus(
                    agentId,
                    FederationTunnelStatus.CONNECTING,
                    agent.linkedPeerId(),
                    null,
                    null,
                    agent.sessionTokenEnc()
            );
            try {
                AgentRuntime runtime = new AgentRuntime(agentId);
                runtimes.put(agentId, runtime);
                URI uri = buildUri(agentStore.findById(agentId).orElseThrow());
                WebSocket webSocket = httpClient.newWebSocketBuilder()
                        .connectTimeout(Duration.ofSeconds(15))
                        .buildAsync(uri, new AgentListener(agentId, runtime))
                        .join();
                runtime.webSocket = webSocket;
            } catch (Exception e) {
                log.warn("Failed to connect outbound agent {}: {}", agent.name(), e.getMessage());
                FederationOutboundAgent current = agentStore.findById(agentId).orElse(agent);
                agentStore.updateStatus(
                        agentId,
                        FederationTunnelStatus.FAILED,
                        current.linkedPeerId(),
                        e.getMessage(),
                        null,
                        current.sessionTokenEnc()
                );
                scheduleReconnect(agentId, 10);
            }
        }
    }

    public void disconnect(UUID agentId) {
        synchronized (connectLocks.computeIfAbsent(agentId, ignored -> new Object())) {
            disconnectRuntime(agentId);
            FederationOutboundAgent agent = agentStore.findById(agentId).orElse(null);
            if (agent == null) {
                return;
            }
            agentStore.updateStatus(
                    agentId,
                    FederationTunnelStatus.DISCONNECTED,
                    agent.linkedPeerId(),
                    null,
                    agent.lastConnectedAt(),
                    agent.sessionTokenEnc()
            );
        }
    }

    private void disconnectRuntime(UUID agentId) {
        AgentRuntime runtime = runtimes.remove(agentId);
        if (runtime != null && runtime.webSocket != null) {
            runtime.webSocket.sendClose(WebSocket.NORMAL_CLOSURE, "shutdown").join();
        }
    }

    @EventListener
    public void onObjectChange(ObjectChangeEvent event) {
        if (event.path() == null) {
            return;
        }
        if (FederationPaths.isCatalogMirrorPath(event.path())) {
            return;
        }
        if (event.type() != ObjectChangeType.UPDATED && event.type() != ObjectChangeType.VARIABLE_UPDATED) {
            return;
        }
        for (FederationOutboundAgent agent : agentStore.listEnabled()) {
            if (!shouldExportPath(agent, event.path())) {
                continue;
            }
            deliverOrBuffer(agent.id(), event.path(), event.variableName(), Instant.now());
        }
    }

    private void deliverOrBuffer(UUID agentId, String path, String variableName, Instant occurredAt) {
        AgentRuntime runtime = runtimes.get(agentId);
        if (runtime != null && runtime.webSocket != null) {
            if (sendEventNotify(runtime.webSocket, path, variableName, null, occurredAt)) {
                return;
            }
        }
        eventBufferRegistry.enqueue(agentId, path, variableName, occurredAt);
    }

    private boolean sendEventNotify(
            WebSocket webSocket,
            String path,
            String variableName,
            Long seq,
            Instant occurredAt
    ) {
        try {
            webSocket.sendText(
                    FederationTunnelProtocol.eventNotify(path, variableName, seq, occurredAt, objectMapper),
                    true
            ).join();
            return true;
        } catch (Exception e) {
            log.debug("Failed to push event_notify: {}", e.getMessage());
            return false;
        }
    }

    private void replayBufferedEvents(UUID agentId) {
        AgentRuntime runtime = runtimes.get(agentId);
        if (runtime == null || runtime.webSocket == null) {
            return;
        }
        for (FederationOutboundEventBuffer.BufferedEvent event : eventBufferRegistry.drain(agentId)) {
            if (!sendEventNotify(
                    runtime.webSocket,
                    event.path(),
                    event.variableName(),
                    event.seq(),
                    event.occurredAt()
            )) {
                eventBufferRegistry.enqueue(agentId, event.path(), event.variableName(), event.occurredAt());
                break;
            }
        }
    }

    private static boolean shouldExportPath(FederationOutboundAgent agent, String path) {
        String prefix = agent.pathPrefix() == null || agent.pathPrefix().isBlank()
                ? "root.platform"
                : agent.pathPrefix().trim().replaceAll("\\.+$", "");
        return path.equals(prefix) || path.startsWith(prefix + ".");
    }

    @PreDestroy
    public void shutdown() {
        scheduler.shutdownNow();
        proxyExecutor.shutdownNow();
        runtimes.keySet().forEach(this::disconnect);
    }

    private void onOpen(UUID agentId) {
        FederationOutboundAgent agent = agentStore.findById(agentId).orElseThrow();
        agentStore.updateStatus(
                agentId,
                FederationTunnelStatus.CONNECTED,
                agent.linkedPeerId(),
                null,
                Instant.now(),
                agent.sessionTokenEnc()
        );
        replayBufferedEvents(agentId);
        scheduler.scheduleAtFixedRate(() -> sendPing(agentId), 30, 30, TimeUnit.SECONDS);
    }

    private void onText(UUID agentId, String payload) {
        JsonNode node = null;
        try {
            node = objectMapper.readTree(payload);
            String type = node.path("type").asString("");
            switch (type) {
                case FederationTunnelProtocol.TYPE_REGISTERED -> handleRegistered(agentId, node);
                case FederationTunnelProtocol.TYPE_TOKEN_REFRESH -> handleTokenRefresh(agentId, node);
                case FederationTunnelProtocol.TYPE_PROXY_REQUEST -> {
                    JsonNode requestNode = node;
                    proxyExecutor.execute(() -> {
                        try {
                            handleProxyRequest(agentId, requestNode);
                        } catch (Exception e) {
                            log.warn("Agent {} proxy request failed: {}", agentId, e.getMessage());
                            handleProxyFailure(agentId, requestNode, e);
                        }
                    });
                }
                case FederationTunnelProtocol.TYPE_PONG -> { /* keepalive */ }
                default -> log.debug("Agent {} ignored message type {}", agentId, type);
            }
        } catch (Exception e) {
            log.warn("Agent {} failed to handle message: {}", agentId, e.getMessage());
            if (node != null) {
                handleProxyFailure(agentId, node, e);
            }
        }
    }

    private void handleProxyFailure(UUID agentId, JsonNode node, Exception error) {
        if (!FederationTunnelProtocol.TYPE_PROXY_REQUEST.equals(node.path("type").asString(""))) {
            return;
        }
        AgentRuntime runtime = runtimes.get(agentId);
        if (runtime == null || runtime.webSocket == null) {
            return;
        }
        String id = node.path("id").asString(null);
        if (id == null) {
            return;
        }
        try {
            runtime.webSocket.sendText(
                    FederationTunnelProtocol.proxyResponse(
                            id,
                            500,
                            objectMapper.createObjectNode().put("error", error.getMessage()),
                            objectMapper
                    ),
                    true
            ).join();
        } catch (Exception sendError) {
            log.debug("Agent {} failed to send proxy error response: {}", agentId, sendError.getMessage());
        }
    }

    private void onClose(UUID agentId, int statusCode) {
        runtimes.remove(agentId);
        FederationOutboundAgent agent = agentStore.findById(agentId).orElse(null);
        if (agent == null || !agent.enabled()) {
            return;
        }
        agentStore.updateStatus(
                agentId,
                FederationTunnelStatus.RECONNECTING,
                agent.linkedPeerId(),
                "WebSocket closed: " + statusCode,
                agent.lastConnectedAt(),
                agent.sessionTokenEnc()
        );
        scheduleReconnect(agentId, 5);
    }

    private void handleRegistered(UUID agentId, JsonNode node) throws Exception {
        UUID peerId = UUID.fromString(node.path("peerId").asString());
        String token = node.path("token").asString(null);
        String tokenEnc = token != null && secretCipher.isEnabled() ? secretCipher.encrypt(token) : null;
        agentStore.updateStatus(agentId, FederationTunnelStatus.CONNECTED, peerId, null, Instant.now(), tokenEnc);
    }

    private void handleTokenRefresh(UUID agentId, JsonNode node) {
        String token = node.path("token").asString(null);
        if (token == null || !secretCipher.isEnabled()) {
            return;
        }
        UUID linkedPeerId = agentStore.findById(agentId).map(FederationOutboundAgent::linkedPeerId).orElse(null);
        agentStore.updateStatus(agentId, FederationTunnelStatus.CONNECTED, linkedPeerId, null, Instant.now(), secretCipher.encrypt(token));
    }

    private void handleProxyRequest(UUID agentId, JsonNode node) throws Exception {
        AgentRuntime runtime = runtimes.get(agentId);
        if (runtime == null || runtime.webSocket == null) {
            throw new IllegalStateException("Tunnel agent runtime is not ready");
        }
        String id = node.path("id").asString();
        String method = node.path("method").asString("GET");
        String path = node.path("path").asString();
        String query = node.path("query").asString(null);
        String body = node.has("body") ? objectMapper.writeValueAsString(node.get("body")) : null;
        log.debug("Agent {} handling tunnel proxy {} {}", agentId, method, path);
        var result = localProxyService.dispatch(method, path, query, body);
        JsonNode responseBody = result.body();
        if (result.error() != null && responseBody == null) {
            responseBody = objectMapper.createObjectNode().put("error", result.error());
        }
        String responseJson = FederationTunnelProtocol.proxyResponse(id, result.status(), responseBody, objectMapper);
        if (responseJson.length() > 3_000_000) {
            log.warn("Agent {} proxy response for {} is {} bytes", agentId, path, responseJson.length());
        }
        runtime.webSocket.sendText(responseJson, true).join();
    }

    private void sendPing(UUID agentId) {
        AgentRuntime runtime = runtimes.get(agentId);
        if (runtime == null || runtime.webSocket == null) {
            return;
        }
        try {
            runtime.webSocket.sendText(FederationTunnelProtocol.ping(objectMapper), true).join();
        } catch (Exception e) {
            log.debug("Ping failed for agent {}: {}", agentId, e.getMessage());
        }
    }

    private void scheduleReconnect(UUID agentId, int delaySeconds) {
        FederationOutboundAgent agent = agentStore.findById(agentId).orElse(null);
        if (agent == null || !agent.enabled()) {
            return;
        }
        scheduler.schedule(() -> connectNow(agentId), delaySeconds, TimeUnit.SECONDS);
    }

    private URI buildUri(FederationOutboundAgent agent) {
        String wsUrl = toWebSocketUrl(agent);
        StringBuilder uri = new StringBuilder(wsUrl).append("/ws/federation/tunnel?");
        if (agent.sessionTokenEnc() != null && agent.linkedPeerId() != null && secretCipher.isEnabled()) {
            String token = secretCipher.decrypt(agent.sessionTokenEnc());
            uri.append("sessionToken=").append(encode(token));
            uri.append("&peerId=").append(agent.linkedPeerId());
        } else {
            String code = secretCipher.decrypt(agent.registrationCodeEnc());
            uri.append("registrationCode=").append(encode(code));
            uri.append("&siteName=").append(encode(agent.name()));
            uri.append("&pathPrefix=").append(encode(agent.pathPrefix()));
        }
        return URI.create(uri.toString());
    }

    private static String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }

    private static String toWebSocketUrl(FederationOutboundAgent agent) {
        String base = agent.hubBaseUrl().trim();
        if (base.startsWith("https://")) {
            return "wss://" + base.substring("https://".length());
        }
        if (base.startsWith("http://")) {
            return "ws://" + base.substring("http://".length());
        }
        return base;
    }

    private final class AgentListener implements WebSocket.Listener {
        private final UUID agentId;
        private final AgentRuntime runtime;
        private final StringBuilder buffer = new StringBuilder();

        private AgentListener(UUID agentId, AgentRuntime runtime) {
            this.agentId = agentId;
            this.runtime = runtime;
        }

        @Override
        public void onOpen(WebSocket webSocket) {
            runtime.webSocket = webSocket;
            webSocket.request(1);
            FederationTunnelAgentService.this.onOpen(agentId);
            WebSocket.Listener.super.onOpen(webSocket);
        }

        @Override
        public java.util.concurrent.CompletionStage<?> onText(WebSocket webSocket, CharSequence data, boolean last) {
            buffer.append(data);
            if (last) {
                FederationTunnelAgentService.this.onText(agentId, buffer.toString());
                buffer.setLength(0);
            }
            webSocket.request(1);
            return CompletableFuture.completedFuture(null);
        }

        @Override
        public java.util.concurrent.CompletionStage<?> onClose(WebSocket webSocket, int statusCode, String reason) {
            FederationTunnelAgentService.this.onClose(agentId, statusCode);
            return CompletableFuture.completedFuture(null);
        }

        @Override
        public void onError(WebSocket webSocket, Throwable error) {
            FederationTunnelAgentService.this.onClose(agentId, 1011);
        }
    }

    private static final class AgentRuntime {
        private final UUID agentId;
        private WebSocket webSocket;

        private AgentRuntime(UUID agentId) {
            this.agentId = agentId;
        }
    }
}
