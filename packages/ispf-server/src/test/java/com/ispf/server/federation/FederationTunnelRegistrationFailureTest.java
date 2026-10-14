package com.ispf.server.federation;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.socket.WebSocketSession;
import tools.jackson.databind.ObjectMapper;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class FederationTunnelRegistrationFailureTest {

    @Mock
    private ObjectMapper objectMapper;
    @Mock
    private FederationInboundRegistrationService registrationService;
    @Mock
    private FederationTunnelHubService hubService;
    @Mock
    private FederationTunnelSessionStore sessionStore;
    @Mock
    private WebSocketSession session;

    @Test
    void failedReconnectIsNotLeftConnectedAndIsVisibleOnThePeer() throws Exception {
        UUID peerId = UUID.randomUUID();
        Map<String, Object> attributes = new HashMap<>();
        attributes.put("tunnelMode", "reconnect");
        attributes.put("sessionToken", "bad");
        attributes.put("peerId", peerId);
        when(session.getAttributes()).thenReturn(attributes);
        when(registrationService.resolveReconnectPeer("bad", peerId))
                .thenThrow(new IllegalArgumentException("Invalid session token"));

        new FederationTunnelWebSocketHandler(objectMapper, registrationService, hubService, sessionStore)
                .afterConnectionEstablished(session);

        verify(hubService, never()).registerSession(any(), any());
        verify(hubService).unregisterSession(peerId, session);
        verify(hubService).recordRegistrationFailure(peerId, "Invalid session token");
        verify(session).close(any());
    }
}
