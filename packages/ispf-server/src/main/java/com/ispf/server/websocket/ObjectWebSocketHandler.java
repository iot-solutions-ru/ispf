package com.ispf.server.websocket;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ispf.server.object.ObjectChangeEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import java.io.IOException;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class ObjectWebSocketHandler extends TextWebSocketHandler {

    private final Set<WebSocketSession> sessions = ConcurrentHashMap.newKeySet();
    private final ObjectMapper objectMapper;

    public ObjectWebSocketHandler(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    public void afterConnectionEstablished(WebSocketSession session) {
        sessions.add(session);
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        sessions.remove(session);
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
            if (session.isOpen()) {
                session.sendMessage(payload);
            }
        }
    }
}
