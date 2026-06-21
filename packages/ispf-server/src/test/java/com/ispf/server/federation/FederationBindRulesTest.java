package com.ispf.server.federation;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FederationBindRulesTest {

    private static final String PATH = "root.platform.devices.snmp-localhost";
    private static final UUID PEER_ID = UUID.randomUUID();

    @Test
    void allowsSamePathOnRemotePeer() {
        FederationPeer remotePeer = peer("http://92.63.104.121", 8080);
        assertDoesNotThrow(() -> FederationBindRules.validate(
                PATH,
                PATH,
                remotePeer,
                8080,
                Optional.empty()
        ));
    }

    @Test
    void rejectsSamePathOnLoopbackPeer() {
        FederationPeer loopback = peer("http://127.0.0.1:9090", 9090);
        ResponseStatusException ex = assertThrows(ResponseStatusException.class, () ->
                FederationBindRules.validate(PATH, PATH, loopback, 9090, Optional.empty())
        );
        assertEquals(HttpStatus.BAD_REQUEST, ex.getStatusCode());
        assertTrue(ex.getReason().contains("same ISPF instance"));
    }

    @Test
    void allowsRebindSamePathOnRemotePeerWhenAlreadyFederated() {
        FederationPeer remotePeer = peer("http://edge.example:8080", 8080);
        var selfTarget = new FederationProxyService.FederationProxyTarget(PATH, PEER_ID, PATH);
        assertDoesNotThrow(() -> FederationBindRules.validate(
                PATH,
                PATH,
                remotePeer,
                8080,
                Optional.of(selfTarget)
        ));
    }

    @Test
    void rejectsRemotePathPointingAtFederatedMirrorOfLocalPath() {
        FederationPeer remotePeer = peer("http://edge.example:8080", 8080);
        String mirrorPath = "root.platform.federation.edge.devices.snmp-localhost";
        var mirrorTarget = new FederationProxyService.FederationProxyTarget(
                mirrorPath,
                PEER_ID,
                PATH
        );
        ResponseStatusException ex = assertThrows(ResponseStatusException.class, () ->
                FederationBindRules.validate(PATH, mirrorPath, remotePeer, 8080, Optional.of(mirrorTarget))
        );
        assertEquals(HttpStatus.BAD_REQUEST, ex.getStatusCode());
        assertTrue(ex.getReason().contains("already targets this local path"));
    }

    private static FederationPeer peer(String baseUrl, int port) {
        return new FederationPeer(
                PEER_ID,
                "test-peer",
                baseUrl,
                null,
                "root.platform",
                true,
                null,
                FederationConnectionMode.HTTP_PULL,
                FederationAuthMode.STATIC_TOKEN,
                null,
                null,
                null,
                FederationAuthStatus.OK,
                null,
                null,
                null,
                null
        );
    }
}
