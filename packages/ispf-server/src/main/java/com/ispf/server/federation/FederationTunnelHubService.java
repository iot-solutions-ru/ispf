package com.ispf.server.federation;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import com.ispf.server.object.ObjectChangeEvent;
import com.ispf.server.object.ObjectChangeType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;

import java.io.IOException;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

@Service
public class FederationTunnelHubService {

    private static final Logger log = LoggerFactory.getLogger(FederationTunnelHubService.class);
    private static final long PROXY_TIMEOUT_SECONDS = 120;

    private final ObjectMapper objectMapper;
    private final FederationWebSocketFanoutService webSocketFanout;
    private final ApplicationEventPublisher eventPublisher;
    private final Map<UUID, WebSocketSession> sessionsByPeer = new ConcurrentHashMap<>();
    private final Map<String, CompletableFuture<FederationTunnelProxyResult>> pending = new ConcurrentHashMap<>();

    public FederationTunnelHubService(
            ObjectMapper objectMapper,
            FederationWebSocketFanoutService webSocketFanout,
            ApplicationEventPublisher eventPublisher
    ) {
        this.objectMapper = objectMapper;
        this.webSocketFanout = webSocketFanout;
        this.eventPublisher = eventPublisher;
    }

    public void registerSession(UUID peerId, WebSocketSession session) {
        WebSocketSession previous = sessionsByPeer.put(peerId, session);
        if (previous != null && previous.isOpen() && previous != session) {
            try {
                previous.close();
            } catch (IOException e) {
                log.debug("Failed to close previous tunnel session for peer {}", peerId);
            }
        }
    }

    public void unregisterSession(UUID peerId, WebSocketSession session) {
        sessionsByPeer.computeIfPresent(peerId, (id, current) -> current == session ? null : current);
    }

    public boolean isConnected(UUID peerId) {
        WebSocketSession session = sessionsByPeer.get(peerId);
        return session != null && session.isOpen();
    }

    public Optional<WebSocketSession> sessionForPeer(UUID peerId) {
        WebSocketSession session = sessionsByPeer.get(peerId);
        if (session == null || !session.isOpen()) {
            return Optional.empty();
        }
        return Optional.of(session);
    }

    public JsonNode dispatch(UUID peerId, String method, String pathAndQuery, String body) {
        int q = pathAndQuery.indexOf('?');
        String path = q >= 0 ? pathAndQuery.substring(0, q) : pathAndQuery;
        String query = q >= 0 ? pathAndQuery.substring(q + 1) : null;
        FederationTunnelProxyResult result = dispatchRaw(peerId, method, path, query, body);
        if (result.status() >= 400) {
            if (result.status() == 404) {
                throw new ResponseStatusException(HttpStatus.NOT_FOUND, result.error());
            }
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, result.error());
        }
        return result.body();
    }

    public FederationTunnelProxyResult dispatchRaw(
            UUID peerId,
            String method,
            String path,
            String query,
            String body
    ) {
        WebSocketSession session = sessionsByPeer.get(peerId);
        if (session == null || !session.isOpen()) {
            throw new ResponseStatusException(
                    HttpStatus.SERVICE_UNAVAILABLE,
                    "Tunnel agent is not connected for peer " + peerId
            );
        }
        String requestId = UUID.randomUUID().toString();
        CompletableFuture<FederationTunnelProxyResult> future = new CompletableFuture<>();
        pending.put(requestId, future);
        try {
            session.sendMessage(new TextMessage(
                    FederationTunnelProtocol.proxyRequest(requestId, method, path, query, body, objectMapper)
            ));
        } catch (IOException e) {
            pending.remove(requestId);
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Failed to send tunnel proxy request", e);
        } catch (Exception e) {
            pending.remove(requestId);
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Failed to encode tunnel proxy request", e);
        }
        try {
            return future.get(PROXY_TIMEOUT_SECONDS, TimeUnit.SECONDS);
        } catch (TimeoutException e) {
            pending.remove(requestId);
            log.warn("Tunnel proxy timed out for peer {} {} {}", peerId, method, path);
            throw new ResponseStatusException(HttpStatus.GATEWAY_TIMEOUT, "Tunnel proxy request timed out");
        } catch (Exception e) {
            pending.remove(requestId);
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Tunnel proxy request failed: " + e.getMessage(), e);
        }
    }

    public void handleInboundMessage(WebSocketSession session, JsonNode node) throws IOException {
        String type = node.path("type").asString("");
        switch (type) {
            case FederationTunnelProtocol.TYPE_PONG -> {
                UUID peerId = peerId(session);
                if (peerId != null) {
                    session.getAttributes().put("lastPongAt", Instant.now().toString());
                }
            }
            case FederationTunnelProtocol.TYPE_PROXY_RESPONSE -> completeProxyResponse(node);
            case FederationTunnelProtocol.TYPE_EVENT_NOTIFY -> handleEventNotify(session, node);
            default -> log.debug("Ignoring tunnel message type {} from {}", type, session.getId());
        }
    }

    private void handleEventNotify(WebSocketSession session, JsonNode node) {
        UUID peerId = peerId(session);
        if (peerId == null) {
            return;
        }
        String remotePath = node.path("path").asString(null);
        String variableName = node.path("variableName").asString(null);
        if (remotePath == null || remotePath.isBlank()) {
            return;
        }
        if (FederationPaths.isCatalogMirrorPath(remotePath)) {
            return;
        }
        FederationPeer peer = peer(session);
        if (peer == null) {
            return;
        }
        try {
            String localPath = FederationService.localMirrorPath(peer, remotePath);
            webSocketFanout.notifyFederatedPathUpdated(localPath, variableName);
            eventPublisher.publishEvent(ObjectChangeEvent.of(ObjectChangeType.UPDATED, localPath));
        } catch (RuntimeException e) {
            log.debug("Ignoring tunnel event_notify for peer {} path {}: {}", peerId, remotePath, e.getMessage());
        }
    }

    private void completeProxyResponse(JsonNode node) {
        String id = node.path("id").asString(null);
        if (id == null) {
            return;
        }
        CompletableFuture<FederationTunnelProxyResult> future = pending.remove(id);
        if (future == null) {
            return;
        }
        int status = node.path("status").asInt(500);
        JsonNode body = node.get("body");
        String error = node.path("error").asString(null);
        future.complete(new FederationTunnelProxyResult(status, body, error));
    }

    public void sendTokenRefresh(UUID peerId, String token, String tokenExpiresAt) {
        sessionForPeer(peerId).ifPresent(session -> {
            try {
                session.sendMessage(new TextMessage(
                        FederationTunnelProtocol.tokenRefresh(token, tokenExpiresAt, objectMapper)
                ));
            } catch (IOException e) {
                log.warn("Failed to send token_refresh to peer {}: {}", peerId, e.getMessage());
            } catch (Exception e) {
                log.warn("Failed to encode token_refresh for peer {}: {}", peerId, e.getMessage());
            }
        });
    }

    static UUID peerId(WebSocketSession session) {
        Object raw = session.getAttributes().get("peerId");
        if (raw instanceof UUID uuid) {
            return uuid;
        }
        if (raw instanceof String text && !text.isBlank()) {
            return UUID.fromString(text);
        }
        return null;
    }

    static FederationPeer peer(WebSocketSession session) {
        Object raw = session.getAttributes().get("peer");
        return raw instanceof FederationPeer federationPeer ? federationPeer : null;
    }

    public record FederationTunnelProxyResult(int status, JsonNode body, String error) {
    }
}
