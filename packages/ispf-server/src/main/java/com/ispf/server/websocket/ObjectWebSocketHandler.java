package com.ispf.server.websocket;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import com.ispf.server.application.catalog.ApplicationEventCatalogService;
import com.ispf.server.config.WebSocketProperties;
import com.ispf.server.concurrent.ElasticWorkerLauncher;
import com.ispf.server.federation.FederationPaths;
import com.ispf.server.federation.FederationSubscribePollService;
import com.ispf.server.object.ObjectChangeEvent;
import com.ispf.server.object.ObjectManager;
import com.ispf.server.object.pubsub.ClusterPathInterestStore;
import com.ispf.server.object.pubsub.ObjectWebSocketPathInterestRegistry;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
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
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicInteger;

@Component
public class ObjectWebSocketHandler extends TextWebSocketHandler {

    private static final Logger log = LoggerFactory.getLogger(ObjectWebSocketHandler.class);
    private static final String SUBSCRIBE_ATTR = "subscribedInterest";

    private final Set<WebSocketSession> sessions = ConcurrentHashMap.newKeySet();
    /** Reverse index: subscribed path → sessions interested in that path (path-wide or any variables). */
    private final ConcurrentHashMap<String, Set<WebSocketSession>> pathSubscriptions = new ConcurrentHashMap<>();
    private final WebSocketProperties webSocketProperties;
    private final ConcurrentLinkedQueue<Runnable> pendingSends = new ConcurrentLinkedQueue<>();
    private final AtomicInteger pendingSendCount = new AtomicInteger();
    private ElasticWorkerLauncher sendWorkers;
    private final ObjectMapper objectMapper;
    private final FederationSubscribePollService federationSubscribePollService;
    private final ObjectPresenceService presenceService;
    private final ObjectManager objectManager;
    private final ApplicationEventCatalogService eventCatalogService;
    private final ObjectWebSocketPathInterestRegistry pathInterestRegistry;
    private final ClusterPathInterestStore clusterPathInterestStore;

    public ObjectWebSocketHandler(
            WebSocketProperties webSocketProperties,
            ObjectMapper objectMapper,
            FederationSubscribePollService federationSubscribePollService,
            ObjectPresenceService presenceService,
            ObjectManager objectManager,
            ApplicationEventCatalogService eventCatalogService,
            ObjectWebSocketPathInterestRegistry pathInterestRegistry,
            ClusterPathInterestStore clusterPathInterestStore
    ) {
        this.webSocketProperties = webSocketProperties;
        this.objectMapper = objectMapper;
        this.federationSubscribePollService = federationSubscribePollService;
        this.presenceService = presenceService;
        this.objectManager = objectManager;
        this.eventCatalogService = eventCatalogService;
        this.pathInterestRegistry = pathInterestRegistry;
        this.clusterPathInterestStore = clusterPathInterestStore;
    }

    @PostConstruct
    void startSendWorkers() {
        sendWorkers = new ElasticWorkerLauncher(
                webSocketProperties.resolvedSendElastic(),
                pendingSendCount::get,
                "ws-object-send",
                this::drainOneSend
        );
        sendWorkers.start();
    }

    private boolean drainOneSend() {
        Runnable task = pendingSends.poll();
        if (task == null) {
            return false;
        }
        try {
            task.run();
        } finally {
            pendingSendCount.decrementAndGet();
        }
        return true;
    }

    @PreDestroy
    void shutdownSendWorkers() {
        if (sendWorkers != null) {
            sendWorkers.close();
        }
        Runnable task;
        while ((task = pendingSends.poll()) != null) {
            pendingSendCount.decrementAndGet();
            task.run();
        }
    }

    @Override
    public void afterConnectionEstablished(WebSocketSession session) {
        sessions.add(session);
        // No broadcast: live interest starts only after an explicit non-empty subscribe.
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
        return List.of();
    }

    private void handleSubscribe(WebSocketSession session, JsonNode node) throws IOException {
        SessionInterest next = SessionInterest.parse(node);
        @SuppressWarnings("unchecked")
        SessionInterest previous = (SessionInterest) session.getAttributes().get(SUBSCRIBE_ATTR);
        session.getAttributes().put(SUBSCRIBE_ATTR, next);
        updateSubscriptionIndex(session, previous, next);
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
        if (event.value() != null) {
            message.put("value", event.value());
        }
        if (event.previousValue() != null) {
            message.put("previousValue", event.previousValue());
        }
        TextMessage payload;
        try {
            payload = new TextMessage(objectMapper.writeValueAsString(message));
        } catch (tools.jackson.core.JacksonException e) {
            log.warn("Failed to serialize object-change message for {}: {}", event.path(), e.getMessage());
            return;
        }
        for (WebSocketSession session : sessionsForEvent(event.path(), event.variableName())) {
            if (!session.isOpen()) {
                continue;
            }
            sendAsync(session, payload);
        }
    }

    private void sendAsync(WebSocketSession session, TextMessage payload) {
        pendingSendCount.incrementAndGet();
        pendingSends.offer(() -> {
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
        sendWorkers.signalWork();
    }

    private Set<WebSocketSession> sessionsForEvent(String eventPath, String variableName) {
        Set<WebSocketSession> candidates = new HashSet<>();
        if (pathSubscriptions.isEmpty()) {
            return candidates;
        }
        String prefix = "";
        for (String part : eventPath.split("\\.")) {
            prefix = prefix.isEmpty() ? part : prefix + "." + part;
            Set<WebSocketSession> subs = pathSubscriptions.get(prefix);
            if (subs != null) {
                candidates.addAll(subs);
            }
        }
        String childPrefix = eventPath + ".";
        for (Map.Entry<String, Set<WebSocketSession>> entry : pathSubscriptions.entrySet()) {
            if (entry.getKey().startsWith(childPrefix)) {
                candidates.addAll(entry.getValue());
            }
        }
        if (variableName == null || variableName.isBlank()) {
            return candidates;
        }
        Set<WebSocketSession> targets = new HashSet<>();
        for (WebSocketSession session : candidates) {
            Object raw = session.getAttributes().get(SUBSCRIBE_ATTR);
            if (raw instanceof SessionInterest interest && interest.allowsVariable(eventPath, variableName)) {
                targets.add(session);
            }
        }
        return targets;
    }

    private void updateSubscriptionIndex(WebSocketSession session, SessionInterest previous, SessionInterest next) {
        if (previous != null) {
            clearInterest(session, previous);
        }
        if (next == null || next.isEmpty()) {
            return;
        }
        for (String path : next.allPaths()) {
            pathSubscriptions.computeIfAbsent(path, ignored -> ConcurrentHashMap.newKeySet()).add(session);
            if (next.isPathWide(path)) {
                pathInterestRegistry.subscribePath(path);
                clusterPathInterestStore.subscribePath(path);
            } else {
                Set<String> variables = next.variablesFor(path);
                pathInterestRegistry.subscribeVariables(path, variables);
                // Cluster keeps path-level interest so owners still publish across replicas.
                clusterPathInterestStore.subscribePath(path);
            }
        }
    }

    private void clearInterest(WebSocketSession session, SessionInterest interest) {
        for (String path : interest.allPaths()) {
            Set<WebSocketSession> subs = pathSubscriptions.get(path);
            if (subs != null) {
                subs.remove(session);
                if (subs.isEmpty()) {
                    pathSubscriptions.remove(path);
                }
            }
            if (interest.isPathWide(path)) {
                pathInterestRegistry.unsubscribePath(path);
                clusterPathInterestStore.unsubscribePath(path);
            } else {
                Set<String> variables = interest.variablesFor(path);
                pathInterestRegistry.unsubscribeVariables(path, variables);
                clusterPathInterestStore.unsubscribePath(path);
            }
        }
    }

    private void removeSessionFromIndex(WebSocketSession session) {
        Object raw = session.getAttributes().get(SUBSCRIBE_ATTR);
        if (raw instanceof SessionInterest interest) {
            clearInterest(session, interest);
        }
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
            if (raw instanceof SessionInterest interest) {
                for (String path : interest.allPaths()) {
                    if (FederationPaths.isCatalogMirrorPath(path)) {
                        federated.add(path);
                    }
                }
            }
        }
        federationSubscribePollService.replaceSubscriptions(federated);
    }

    /**
     * Parsed WS {@code subscribe} payload.
     * Path without {@code variablesByPath} entry (or with {@code "*"}) = path-wide interest.
     */
    static final class SessionInterest {
        private final Set<String> pathWide;
        private final Map<String, Set<String>> variablesByPath;

        SessionInterest(Set<String> pathWide, Map<String, Set<String>> variablesByPath) {
            this.pathWide = Set.copyOf(pathWide);
            this.variablesByPath = Map.copyOf(variablesByPath);
        }

        static SessionInterest parse(JsonNode node) {
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
            Map<String, Set<String>> variablesByPath = new java.util.HashMap<>();
            JsonNode varsNode = node.get("variablesByPath");
            if (varsNode != null && varsNode.isObject()) {
                varsNode.properties().forEach(property -> {
                    String path = property.getKey() == null ? "" : property.getKey().trim();
                    if (path.isBlank() || !paths.contains(path)) {
                        return;
                    }
                    Set<String> variables = new HashSet<>();
                    JsonNode list = property.getValue();
                    if (list != null && list.isArray()) {
                        list.forEach(entry -> {
                            if (entry.isString()) {
                                String name = entry.asString().trim();
                                if (!name.isBlank()) {
                                    variables.add(name);
                                }
                            }
                        });
                    }
                    if (!variables.isEmpty() && !variables.contains("*")) {
                        variablesByPath.put(path, Set.copyOf(variables));
                    }
                });
            }
            Set<String> pathWide = new HashSet<>();
            for (String path : paths) {
                if (!variablesByPath.containsKey(path)) {
                    pathWide.add(path);
                }
            }
            return new SessionInterest(pathWide, variablesByPath);
        }

        boolean isEmpty() {
            return pathWide.isEmpty() && variablesByPath.isEmpty();
        }

        Set<String> allPaths() {
            Set<String> all = new HashSet<>(pathWide);
            all.addAll(variablesByPath.keySet());
            return all;
        }

        boolean isPathWide(String path) {
            return pathWide.contains(path);
        }

        Set<String> variablesFor(String path) {
            return variablesByPath.getOrDefault(path, Set.of());
        }

        boolean allowsVariable(String eventPath, String variableName) {
            for (String path : pathWide) {
                if (pathMatches(path, eventPath)) {
                    return true;
                }
            }
            for (Map.Entry<String, Set<String>> entry : variablesByPath.entrySet()) {
                if (pathMatches(entry.getKey(), eventPath) && entry.getValue().contains(variableName)) {
                    return true;
                }
            }
            return false;
        }

        private static boolean pathMatches(String subscribed, String eventPath) {
            return eventPath.equals(subscribed)
                    || eventPath.startsWith(subscribed + ".")
                    || subscribed.startsWith(eventPath + ".");
        }
    }
}
