package com.ispf.server.federation;

import com.ispf.server.security.IspfSecretCipher;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class FederationPeerAuthStatusMissingPeerTest {

    @Mock
    private FederationPeerStore peerStore;
    @Mock
    private FederationService federationService;
    @Mock
    private IspfSecretCipher secretCipher;

    @Test
    void missingPeerIsNotFound() {
        UUID id = UUID.fromString("00000000-0000-0000-0000-000000000000");
        when(peerStore.findById(id)).thenReturn(Optional.empty());

        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> service().authStatus(id)
        );

        assertEquals(HttpStatus.NOT_FOUND, exception.getStatusCode());
        assertEquals("Peer not found: " + id, exception.getReason());
    }

    @Test
    void existingPeerReturnsItsAuthStatus() {
        UUID id = UUID.randomUUID();
        Instant lastAuthAt = Instant.parse("2026-11-19T12:00:00Z");
        FederationPeer peer = new FederationPeer(
                id,
                "remote",
                "http://127.0.0.1:9",
                "token",
                "root.platform",
                true,
                null,
                FederationConnectionMode.HTTP_PULL,
                FederationAuthMode.STATIC_TOKEN,
                null,
                null,
                null,
                FederationAuthStatus.OK,
                lastAuthAt,
                null,
                lastAuthAt,
                lastAuthAt
        );
        when(peerStore.findById(id)).thenReturn(Optional.of(peer));

        FederationPeerAuthService.FederationPeerAuthStatus status = service().authStatus(id);

        assertEquals(id, status.peerId());
        assertEquals(FederationAuthStatus.OK, status.authStatus());
        assertEquals(lastAuthAt, status.lastAuthAt());
    }

    private FederationPeerAuthService service() {
        return new FederationPeerAuthService(peerStore, federationService, secretCipher);
    }
}
