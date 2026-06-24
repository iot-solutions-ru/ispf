package com.ispf.server.websocket;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import com.ispf.server.application.catalog.ApplicationEventCatalogService;
import com.ispf.server.config.IspfRoles;
import com.ispf.server.federation.FederationPaths;
import com.ispf.server.federation.FederationSubscribePollService;
import com.ispf.server.object.ObjectChangeEvent;
import com.ispf.server.object.ObjectManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@Component
public class ObjectWebSocketHandler extends TextWebSocketHandler {

    private static final Logger log = LoggerFactory.getLogger(ObjectWebSocketHandler.class);
    private static final String SUBSCRIBE_ATTR = "subscribedPaths";

    private final Set<WebSocketSession> sessions = ConcurrentHashMap.newKeySet();
    /** Sessions with empty subscription — receive all object-change events. */
    private final Set<WebSocketSession> broadcastSessions = ConcurrentHashMap.newKeySet();
    /** Reverse index: subscribed path → sessions interested in that path. */
    private final ConcurrentHashMap<String, Set<WebSocketSession>> pathSubscriptions = new ConcurrentHashMap<>();
    private final ExecutorService sendExecutor = Executors.newFixedThreadPool(2, runnable -> {
        Thread thread = new Thread(runnable, "ws-object-send");
        thread.setDaemon(true);
        return thread;
    });
    private final ObjectMapper objectMapper;
    private final FederationSubscribePollService federationSubscribePollService;
    private final ObjectPresenceService presenceService;
    private final ObjectManager objectManager;
    private final ApplicationEventCatalogService eventCatalogService;

    public ObjectWebSocketHandler(
            ObjectMapper objectMapper,
            FederationSubscribePollService federationSubscribePollService,
            ObjectPresenceService presenceService,
            ObjectManager objectManager,
            ApplicationEventCatalogService eventCatalogService
    ) {
        this.objectMapper = objectMapper;
        this.federationSubscribePollService = federationSubscribePollService;
        this.presenceService = presenceService;
        this.objectManager = objectManager;
        this.eventCatalogService = eventCatalogService;
    }

    @Override
    public void afterConnectionEstablished(WebSocketSession session) {
        sessions.add(session);
        broadcastSessions.add(session);
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        sessions.remove(session);
        removeSessionFromIndex(session);
        Object username = session.getAttributes().get("username");
        Object path = session.getAttributes().get("presencePath");
        if (username instanceof String user && path instanceof String presencePath) {
            presenceService.remove(presencePath, user);
        }
        refreshFederationPollSubscriptions();
    }

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) throws IOException {
        JsonNode node = objectMapper.readTree(message.getPayload());
        String type = node.path("type").asString("");
        if ("subscribe".equals(type)) {
            handleSubscribe(session, node);
            return;
        }
        if ("subscribe_events".equals(type)) {
            handleSubscribeEvents(session, node);
            return;
        }
        if ("presence".equals(type)) {
            handlePresence(session, node);
        }
    }

    private void handleSubscribeEvents(WebSocketSession session, JsonNode node) throws IOException {
        String appId = node.path("appId").asString(null);
        if (appId == null || appId.isBlank()) {
            session.sendMessage(new TextMessage(objectMapper.writeValueAsString(Map.of(
                    "type", "subscribe_events_result",
                    "accepted", List.of(),
                    "rejected", List.of(Map.of("event", "*", "reason", "APP_ID_REQUIRED"))
            ))));
            return;
        }
        List<String> events = new ArrayList<>();
        JsonNode eventsNode = node.get("events");
        if (eventsNode != null && eventsNode.isArray()) {
            eventsNode.forEach(entry -> {
                if (entry.isString()) {
                    String eventId = entry.asString().trim();
                    if (!eventId.isBlank()) {
                        events.add(eventId);
                    }
                }
            });
        }
        Map<String, Object> result = eventCatalogService.filterSubscribableEvents(
                appId,
                events,
                sessionRoles(session)
        );
        Map<String, Object> response = new java.util.LinkedHashMap<>();
        response.put("type", "subscribe_events_result");
        response.put("appId", appId);
        response.putAll(result);
        session.sendMessage(new TextMessage(objectMapper.writeValueAsString(response)));
    }

    @SuppressWarnings("unchecked")
    private List<String> sessionRoles(WebSocketSession session) {
        Object raw = session.getAttributes().get("roles");
        if (raw instanceof List<?> roles) {
            List<String> normalized = new ArrayList<>();
            for (Object role : roles) {
                if (role instanceof String value && !value.isBlank()) {
                    normalized.add(value);
                }
            }
            if (!normalized.isEmpty()) {
                return normalized;
            }
        }
        return List.of(IspfRoles.ADMIN);
    }

    private void handleSubscribe(WebSocketSession session, JsonNode node) throws IOException {
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
        updateSubscriptionIndex(session, paths);
        refreshFederationPollSubscriptions();
    }

    private void handlePresence(WebSocketSession session, JsonNode node) throws IOException {
        String path = node.path("path").asString(null);
        String username = node.path("username").asString(null);
        String mode = node.path("mode").asString("view");
        if (path == null || username == null) {
            return;
        }
        session.getAttributes().put("username", username);
        session.getAttributes().put("presencePath", path);
        presenceService.heartbeat(path, username, mode);
        Map<String, Object> response = Map.of(
                "type", "presence",
                "path", path,
                "entries", presenceService.listForPath(path).stream()
                        .map(entry -> Map.of(
                                "username", entry.username(),
                                "mode", entry.mode(),
                                "lastSeen", entry.lastSeen().toString()
                        ))
                        .toList()
        );
        session.sendMessage(new TextMessage(objectMapper.writeValueAsString(response)));
    }

    public int activeSessionCount() {
        return (int) sessions.stream().filter(WebSocketSession::isOpen).count();
    }

    @EventListener
    public void onObjectChange(ObjectChangeEvent event) {
        Long revision = event.revision();
        if (revision == null) {
            try {
                revision = objectManager.require(event.path()).revision();
            } catch (Exception ignored) {
                revision = null;
            }
        }
        Map<String, Object> message = new java.util.LinkedHashMap<>();
        message.put("type", event.type().name());
        message.put("path", event.path());
        message.put("variableName", event.variableName() != null ? event.variableName() : "");
        message.put("timestamp", event.timestamp().toString());
        if (revision != null) {
            message.put("revision", revision);
        }
        if (event.changedBy() != null) {
            message.put("changedBy", event.changedBy());
        }
        TextMessage payload;
        try {
            payload = new TextMessage(objectMapper.writeValueAsString(message));
        } catch (tools.jackson.core.JacksonException e) {
            log.warn("Failed to serialize object-change message for {}: {}", event.path(), e.getMessage());
            return;
        }
        for (WebSocketSession session : sessionsForEvent(event.path())) {
            if (!session.isOpen()) {
                continue;
            }
            sendAsync(session, payload);
        }
    }

    private void sendAsync(WebSocketSession session, TextMessage payload) {
        sendExecutor.execute(() -> {
            try {
                if (!session.isOpen()) {
                    return;
                }
                synchronized (session) {
                    if (session.isOpen()) {
                        session.sendMessage(payload);
                    }
                }
            } catch (Exception e) {
                log.warn("WebSocket send failed for session {}: {}", session.getId(), e.getMessage());
            }
        });
    }

    private Set<WebSocketSession> sessionsForEvent(String eventPath) {
        Set<WebSocketSession> targets = new HashSet<>(broadcastSessions);
        if (pathSubscriptions.isEmpty()) {
            return targets;
        }
        String prefix = "";
        for (String part : eventPath.split("\\.")) {
            prefix = prefix.isEmpty() ? part : prefix + "." + part;
            Set<WebSocketSession> subs = pathSubscriptions.get(prefix);
            if (subs != null) {
                targets.addAll(subs);
            }
        }
        String childPrefix = eventPath + ".";
        for (Map.Entry<String, Set<WebSocketSession>> entry : pathSubscriptions.entrySet()) {
            if (entry.getKey().startsWith(childPrefix)) {
                targets.addAll(entry.getValue());
            }
        }
        return targets;
    }

    private void updateSubscriptionIndex(WebSocketSession session, Set<String> paths) {
        removeSessionFromIndex(session);
        if (paths.isEmpty()) {
            broadcastSessions.add(session);
            return;
        }
        broadcastSessions.remove(session);
        for (String path : paths) {
            pathSubscriptions.computeIfAbsent(path, ignored -> ConcurrentHashMap.newKeySet()).add(session);
        }
    }

    private void removeSessionFromIndex(WebSocketSession session) {
        broadcastSessions.remove(session);
        for (Set<WebSocketSession> subs : pathSubscriptions.values()) {
            subs.remove(session);
        }
        pathSubscriptions.entrySet().removeIf(entry -> entry.getValue().isEmpty());
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
