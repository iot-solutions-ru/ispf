package com.ispf.server.federation;

import com.ispf.core.model.DataRecord;
import com.ispf.server.event.EventService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class FederationPeerHealthMonitorTest {

    @Mock
    private FederationPeerStore peerStore;
    @Mock
    private FederationPeerHealthService peerHealthService;
    @Mock
    private EventService eventService;

    private FederationPeerHealthMonitor monitor;
    private UUID peerId;

    @BeforeEach
    void setUp() {
        monitor = new FederationPeerHealthMonitor(peerStore, peerHealthService, eventService, true);
        peerId = UUID.randomUUID();
    }

    @Test
    void firesDegradedEventOnTransitionToRed() {
        FederationPeer peer = peer(peerId, "site-a", true);
        when(peerStore.listAll()).thenReturn(List.of(peer));
        when(peerHealthService.health(peerId)).thenReturn(
                new FederationPeerHealth(peerId, FederationPeerHealthLevel.RED, false, null, null, "Tunnel offline", 0, "Tunnel offline")
        );

        monitor.scanPeerHealth();

        verify(eventService).fire(
                eq(FederationPaths.FEDERATION_ROOT),
                eq(FederationPeerHealthBootstrap.EVENT_PEER_HEALTH_DEGRADED),
                org.mockito.ArgumentMatchers.any(DataRecord.class)
        );
        verify(eventService, never()).fire(
                eq(FederationPaths.FEDERATION_ROOT),
                eq(FederationPeerHealthBootstrap.EVENT_PEER_HEALTH_RECOVERED),
                org.mockito.ArgumentMatchers.any(DataRecord.class)
        );
    }

    @Test
    void firesRecoveredEventAfterRedToGreen() {
        FederationPeer peer = peer(peerId, "site-a", true);
        when(peerStore.listAll()).thenReturn(List.of(peer));
        when(peerHealthService.health(peerId))
                .thenReturn(new FederationPeerHealth(
                        peerId, FederationPeerHealthLevel.RED, false, null, null, "err", 0, "Tunnel offline"))
                .thenReturn(new FederationPeerHealth(
                        peerId, FederationPeerHealthLevel.GREEN, true, Instant.now(), 12L, null, 0, "Healthy"));

        monitor.scanPeerHealth();
        monitor.scanPeerHealth();

        ArgumentCaptor<DataRecord> payload = ArgumentCaptor.forClass(DataRecord.class);
        verify(eventService).fire(
                eq(FederationPaths.FEDERATION_ROOT),
                eq(FederationPeerHealthBootstrap.EVENT_PEER_HEALTH_RECOVERED),
                payload.capture()
        );
        assertThat(payload.getValue().firstRow().get("peerName")).isEqualTo("site-a");
    }

    @Test
    void skipsDisabledPeers() {
        when(peerStore.listAll()).thenReturn(List.of(peer(peerId, "site-a", false)));
        monitor.scanPeerHealth();
        verify(eventService, never()).fire(
                org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.any(DataRecord.class)
        );
    }

    private static FederationPeer peer(UUID id, String name, boolean enabled) {
        return new FederationPeer(
                id,
                name,
                "http://127.0.0.1:8080",
                null,
                "root.platform",
                enabled,
                "",
                FederationConnectionMode.TUNNEL_INBOUND,
                FederationAuthMode.STATIC_TOKEN,
                null,
                null,
                null,
                FederationAuthStatus.OK,
                null,
                null,
                Instant.now(),
                Instant.now()
        );
    }
}
