package com.ispf.server.federation;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import java.io.IOException;
import java.util.UUID;

@Component
public class FederationTunnelWebSocketHandler extends TextWebSocketHandler {

    private static final Logger log = LoggerFactory.getLogger(FederationTunnelWebSocketHandler.class);

    private final ObjectMapper objectMapper;
    private final FederationInboundRegistrationService registrationService;
    private final FederationTunnelHubService hubService;
    private final FederationTunnelSessionStore sessionStore;

    public FederationTunnelWebSocketHandler(
            ObjectMapper objectMapper,
            FederationInboundRegistrationService registrationService,
            FederationTunnelHubService hubService,
            FederationTunnelSessionStore sessionStore
    ) {
        this.objectMapper = objectMapper;
        this.registrationService = registrationService;
        this.hubService = hubService;
        this.sessionStore = sessionStore;
    }

    @Override
    public void afterConnectionEstablished(WebSocketSession session) throws Exception {
        Object mode = session.getAttributes().get("tunnelMode");
        if ("registration".equals(mode)) {
            handleRegistrationConnect(session);
        } else if ("reconnect".equals(mode)) {
            handleReconnectConnect(session);
        } else {
            session.close(CloseStatus.BAD_DATA.withReason("Missing tunnel handshake attributes"));
        }
    }

    private void handleRegistrationConnect(WebSocketSession session) throws IOException {
        String registrationCode = (String) session.getAttributes().get("registrationCode");
        String siteName = (String) session.getAttributes().get("siteName");
        String pathPrefix = (String) session.getAttributes().get("pathPrefix");
        try {
            var result = registrationService.consumeRegistration(registrationCode, siteName, pathPrefix);
            attachPeer(session, result.peer(), result.registrationId());
            session.sendMessage(new TextMessage(FederationTunnelProtocol.registered(
                    result.peer().id(),
                    result.sessionToken(),
                    result.tokenExpiresAt(),
                    objectMapper
            )));
        } catch (Exception e) {
            log.warn("Tunnel registration failed: {}", e.getMessage());
            session.close(CloseStatus.BAD_DATA.withReason(e.getMessage()));
        }
    }

    private void handleReconnectConnect(WebSocketSession session) throws IOException {
        String sessionToken = (String) session.getAttributes().get("sessionToken");
        UUID peerId = (UUID) session.getAttributes().get("peerId");
        try {
            FederationPeer peer = registrationService.resolveReconnectPeer(sessionToken, peerId);
            attachPeer(session, peer, null);
            session.sendMessage(new TextMessage(FederationTunnelProtocol.registered(
                    peer.id(),
                    sessionToken,
                    null,
                    objectMapper
            )));
        } catch (Exception e) {
            log.warn("Tunnel reconnect failed: {}", e.getMessage());
            session.close(CloseStatus.NOT_ACCEPTABLE.withReason(e.getMessage()));
        }
    }

    private void attachPeer(WebSocketSession session, FederationPeer peer, UUID registrationId) {
        session.getAttributes().put("peerId", peer.id());
        session.getAttributes().put("peer", peer);
        hubService.registerSession(peer.id(), session);
        sessionStore.upsert(session.getId(), registrationId, peer.id(), "v1");
    }

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) throws Exception {
        JsonNode node = objectMapper.readTree(message.getPayload());
        String type = node.path("type").asString("");
        if (FederationTunnelProtocol.TYPE_PING.equals(type)) {
            session.sendMessage(new TextMessage(FederationTunnelProtocol.pong(objectMapper)));
            UUID peerId = FederationTunnelHubService.peerId(session);
            if (peerId != null) {
                sessionStore.touchPing(session.getId());
            }
            return;
        }
        hubService.handleInboundMessage(session, node);
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        UUID peerId = FederationTunnelHubService.peerId(session);
        if (peerId != null) {
            hubService.unregisterSession(peerId, session);
            sessionStore.disconnect(session.getId());
            sessionStore.disconnectByPeer(peerId);
        }
    }
}
