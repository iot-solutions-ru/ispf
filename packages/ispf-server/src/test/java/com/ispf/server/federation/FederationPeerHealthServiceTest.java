package com.ispf.server.federation;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class FederationPeerHealthServiceTest {

    @Mock
    private FederationPeerStore peerStore;
    @Mock
    private FederationTunnelHubService tunnelHubService;
    @Mock
    private FederationOutboundAgentStore outboundAgentStore;
    @Mock
    private FederationOutboundEventBufferRegistry bufferRegistry;
    @Mock
    private FederationPeerAuthService authService;

    private FederationPeerHealthService healthService;
    private UUID peerId;

    @BeforeEach
    void setUp() {
        peerId = UUID.randomUUID();
        healthService = new FederationPeerHealthService(
                peerStore,
                tunnelHubService,
                outboundAgentStore,
                bufferRegistry,
                authService,
                15,
                false
        );
    }

    @Test
    void tunnelOfflinePeerIsRed() {
        FederationPeer peer = peer(
                peerId,
                FederationConnectionMode.TUNNEL_INBOUND,
                FederationAuthStatus.OK
        );
        when(peerStore.findById(peerId)).thenReturn(Optional.of(peer));
        when(tunnelHubService.isConnected(peerId)).thenReturn(false);
        when(outboundAgentStore.listAll()).thenReturn(List.of());

        FederationPeerHealth health = healthService.health(peerId);
        assertThat(health.level()).isEqualTo(FederationPeerHealthLevel.RED);
        assertThat(health.summary()).contains("offline");
    }

    @Test
    void recentProxySuccessIsGreen() {
        FederationPeer peer = peer(
                peerId,
                FederationConnectionMode.HTTP_PULL,
                FederationAuthStatus.OK
        );
        when(peerStore.findById(peerId)).thenReturn(Optional.of(peer));
        when(tunnelHubService.isConnected(peerId)).thenReturn(false);
        when(outboundAgentStore.listAll()).thenReturn(List.of());
        healthService.recordProxySuccess(peerId, 42);

        FederationPeerHealth health = healthService.health(peerId);
        assertThat(health.level()).isEqualTo(FederationPeerHealthLevel.GREEN);
        assertThat(health.lastProxyLatencyMs()).isEqualTo(42L);
    }

    @Test
    void pollTunnelPeerRecordsOfflineWhenDisconnected() {
        FederationPeer peer = peer(
                peerId,
                FederationConnectionMode.TUNNEL_INBOUND,
                FederationAuthStatus.OK
        );
        when(tunnelHubService.isConnected(peerId)).thenReturn(false);
        when(peerStore.findById(peerId)).thenReturn(Optional.of(peer));
        when(outboundAgentStore.listAll()).thenReturn(List.of());

        healthService.pollPeer(peer);

        FederationPeerHealth health = healthService.health(peerId);
        assertThat(health.level()).isEqualTo(FederationPeerHealthLevel.RED);
        assertThat(health.summary()).contains("offline");
    }

    private static FederationPeer peer(UUID id, FederationConnectionMode mode, FederationAuthStatus authStatus) {
        Instant now = Instant.now();
        return new FederationPeer(
                id,
                "edge",
                "http://127.0.0.1:8080",
                null,
                "root.platform",
                true,
                null,
                mode,
                FederationAuthMode.STATIC_TOKEN,
                null,
                null,
                null,
                authStatus,
                now,
                null,
                now,
                now
        );
    }
}
