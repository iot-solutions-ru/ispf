package com.ispf.server.federation;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class FederationPeerHealthService {

    private static final long DEFAULT_STALE_MINUTES = 15;

    private final FederationPeerStore peerStore;
    private final FederationTunnelHubService tunnelHubService;
    private final FederationOutboundAgentStore outboundAgentStore;
    private final FederationOutboundEventBufferRegistry bufferRegistry;
    private final long staleAfterMs;
    private final Map<UUID, ProxySnapshot> proxySnapshots = new ConcurrentHashMap<>();

    public FederationPeerHealthService(
            FederationPeerStore peerStore,
            FederationTunnelHubService tunnelHubService,
            FederationOutboundAgentStore outboundAgentStore,
            FederationOutboundEventBufferRegistry bufferRegistry,
            @Value("${ispf.federation.health.stale-minutes:15}") long staleMinutes
    ) {
        this.peerStore = peerStore;
        this.tunnelHubService = tunnelHubService;
        this.outboundAgentStore = outboundAgentStore;
        this.bufferRegistry = bufferRegistry;
        this.staleAfterMs = Duration.ofMinutes(Math.max(1, staleMinutes)).toMillis();
    }

    public void recordProxySuccess(UUID peerId, long latencyMs) {
        proxySnapshots.put(peerId, new ProxySnapshot(Instant.now(), latencyMs, null));
    }

    public void recordProxyFailure(UUID peerId, String error) {
        proxySnapshots.compute(peerId, (id, previous) -> new ProxySnapshot(
                previous != null ? previous.lastSuccessAt() : null,
                previous != null ? previous.lastLatencyMs() : null,
                error
        ));
    }

    public FederationPeerHealth health(UUID peerId) {
        FederationPeer peer = peerStore.findById(peerId)
                .orElseThrow(() -> new IllegalArgumentException("Unknown peer: " + peerId));
        boolean tunnelConnected = tunnelHubService.isConnected(peerId);
        ProxySnapshot proxy = proxySnapshots.get(peerId);
        Instant lastSuccess = proxy != null ? proxy.lastSuccessAt() : null;
        Long latencyMs = proxy != null ? proxy.lastLatencyMs() : null;
        String lastError = proxy != null ? proxy.lastError() : null;

        int pendingBuffer = outboundAgentStore.listAll().stream()
                .filter(agent -> peerId.equals(agent.linkedPeerId()))
                .mapToInt(agent -> bufferRegistry.pendingCount(agent.id()))
                .sum();

        FederationPeerHealthLevel level = resolveLevel(
                peer,
                tunnelConnected,
                lastSuccess,
                lastError,
                pendingBuffer
        );
        return new FederationPeerHealth(
                peerId,
                level,
                tunnelConnected,
                lastSuccess,
                latencyMs,
                lastError,
                pendingBuffer,
                summary(level, peer, tunnelConnected, lastSuccess, pendingBuffer)
        );
    }

    private FederationPeerHealthLevel resolveLevel(
            FederationPeer peer,
            boolean tunnelConnected,
            Instant lastSuccess,
            String lastError,
            int pendingBuffer
    ) {
        if (peer.authStatus() == FederationAuthStatus.FAILED) {
            return FederationPeerHealthLevel.RED;
        }
        if (peer.isTunnelInbound() && !tunnelConnected) {
            return FederationPeerHealthLevel.RED;
        }
        if (lastError != null && (lastSuccess == null || lastSuccess.isBefore(Instant.now().minus(Duration.ofMinutes(5))))) {
            return FederationPeerHealthLevel.RED;
        }
        if (pendingBuffer > 0) {
            return FederationPeerHealthLevel.YELLOW;
        }
        if (lastSuccess == null) {
            return FederationPeerHealthLevel.YELLOW;
        }
        long ageMs = Instant.now().toEpochMilli() - lastSuccess.toEpochMilli();
        if (ageMs > staleAfterMs) {
            return FederationPeerHealthLevel.RED;
        }
        if (ageMs > staleAfterMs / 2) {
            return FederationPeerHealthLevel.YELLOW;
        }
        return FederationPeerHealthLevel.GREEN;
    }

    private static String summary(
            FederationPeerHealthLevel level,
            FederationPeer peer,
            boolean tunnelConnected,
            Instant lastSuccess,
            int pendingBuffer
    ) {
        if (level == FederationPeerHealthLevel.RED && peer.authStatus() == FederationAuthStatus.FAILED) {
            return "Auth failed";
        }
        if (peer.isTunnelInbound() && !tunnelConnected) {
            return "Tunnel offline";
        }
        if (pendingBuffer > 0) {
            return "Buffered events pending replay (" + pendingBuffer + ")";
        }
        if (lastSuccess == null) {
            return "No successful proxy yet";
        }
        return "Healthy";
    }

    private record ProxySnapshot(Instant lastSuccessAt, Long lastLatencyMs, String lastError) {
    }
}
