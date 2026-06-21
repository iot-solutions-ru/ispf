package com.ispf.server.websocket;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import com.ispf.server.federation.FederationPaths;
import com.ispf.server.federation.FederationSubscribePollService;
import com.ispf.server.object.ObjectChangeEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import java.io.IOException;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class ObjectWebSocketHandler extends TextWebSocketHandler {

    private static final String SUBSCRIBE_ATTR = "subscribedPaths";

    private final Set<WebSocketSession> sessions = ConcurrentHashMap.newKeySet();
    private final ObjectMapper objectMapper;
    private final FederationSubscribePollService federationSubscribePollService;

    public ObjectWebSocketHandler(
            ObjectMapper objectMapper,
            FederationSubscribePollService federationSubscribePollService
    ) {
        this.objectMapper = objectMapper;
        this.federationSubscribePollService = federationSubscribePollService;
    }

    @Override
    public void afterConnectionEstablished(WebSocketSession session) {
        sessions.add(session);
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        sessions.remove(session);
        refreshFederationPollSubscriptions();
    }

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) throws IOException {
        JsonNode node = objectMapper.readTree(message.getPayload());
        if (!"subscribe".equals(node.path("type").asString())) {
            return;
        }
        Set<String> paths = new HashSet<>();
        JsonNode pathsNode = node.get("paths");
        if (pathsNode != null && pathsNode.isArray()) {
            pathsNode.forEach(entry -> {
                if (entry.isString()) {
                    String path = entry.asString().trim();
                    if (!path.isBlank()) {
                        paths.add(path);
                    }
                }
            });
        }
        session.getAttributes().put(SUBSCRIBE_ATTR, paths);
        refreshFederationPollSubscriptions();
    }

    public int activeSessionCount() {
        return (int) sessions.stream().filter(WebSocketSession::isOpen).count();
    }

    @EventListener
    public void onObjectChange(ObjectChangeEvent event) throws IOException {
        Map<String, Object> message = Map.of(
                "type", event.type().name(),
                "path", event.path(),
                "variableName", event.variableName() != null ? event.variableName() : "",
                "timestamp", event.timestamp().toString()
        );
        TextMessage payload = new TextMessage(objectMapper.writeValueAsString(message));
        for (WebSocketSession session : sessions) {
            if (!session.isOpen()) {
                continue;
            }
            if (!matchesSubscription(session, event.path())) {
                continue;
            }
            session.sendMessage(payload);
        }
    }

    @SuppressWarnings("unchecked")
    private boolean matchesSubscription(WebSocketSession session, String eventPath) {
        Object raw = session.getAttributes().get(SUBSCRIBE_ATTR);
        if (!(raw instanceof Set<?> subscribed) || subscribed.isEmpty()) {
            return true;
        }
        for (Object entry : subscribed) {
            if (entry instanceof String path && matchesPath(eventPath, path)) {
                return true;
            }
        }
        return false;
    }

    private static boolean matchesPath(String eventPath, String subscribedPath) {
        return eventPath.equals(subscribedPath)
                || eventPath.startsWith(subscribedPath + ".")
                || subscribedPath.startsWith(eventPath + ".");
    }

    @SuppressWarnings("unchecked")
    private void refreshFederationPollSubscriptions() {
        Set<String> federated = new HashSet<>();
        for (WebSocketSession session : sessions) {
            Object raw = session.getAttributes().get(SUBSCRIBE_ATTR);
            if (raw instanceof Set<?> subscribed) {
                for (Object entry : subscribed) {
                    if (entry instanceof String path && FederationPaths.isCatalogMirrorPath(path)) {
                        federated.add(path);
                    }
                }
            }
        }
        federationSubscribePollService.replaceSubscriptions(federated);
    }
}
