package com.ispf.server.federation;

import com.ispf.server.security.IspfSecretCipher;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class FederationPeerAuthRefreshFailureTest {

    @Mock
    private FederationPeerStore peerStore;
    @Mock
    private FederationService federationService;
    @Mock
    private IspfSecretCipher secretCipher;

    @Test
    void failedRefreshDropsStaleTokenAndMarksPeerFailed() {
        UUID id = UUID.randomUUID();
        FederationPeer peer = new FederationPeer(
                id,
                "remote",
                "http://127.0.0.1:9",
                "stale-token",
                "root.platform",
                true,
                null,
                FederationConnectionMode.HTTP_PULL,
                FederationAuthMode.SERVICE_ACCOUNT,
                null,
                "admin",
                "enc",
                FederationAuthStatus.OK,
                Instant.parse("2026-10-01T00:00:00Z"),
                null,
                Instant.parse("2026-10-01T00:00:00Z"),
                Instant.parse("2026-10-01T00:00:00Z")
        );
        when(peerStore.listServiceAccountPeers()).thenReturn(List.of(peer));
        when(secretCipher.decrypt("enc")).thenReturn("admin");
        when(federationService.fetchRemoteLoginToken("http://127.0.0.1:9", "admin", "admin"))
                .thenThrow(new IllegalStateException("login refused"));

        new FederationPeerAuthService(peerStore, federationService, secretCipher).refreshDueTokens();

        verify(peerStore).updateAuthState(
                eq(id),
                isNull(),
                isNull(),
                eq(FederationAuthStatus.FAILED),
                eq(Instant.parse("2026-10-01T00:00:00Z")),
                eq("login refused")
        );
    }
}
