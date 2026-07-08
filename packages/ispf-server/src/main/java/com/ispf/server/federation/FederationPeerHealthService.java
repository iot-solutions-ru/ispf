package com.ispf.server.federation;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Lazy;
import org.springframework.http.HttpStatus;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class FederationPeerHealthService {

    private static final Logger log = LoggerFactory.getLogger(FederationPeerHealthService.class);
    private static final long DEFAULT_STALE_MINUTES = 15;
    private static final String PROBE_PATH = "/api/v1/info";

    private final FederationPeerStore peerStore;
    private final FederationTunnelHubService tunnelHubService;
    private final FederationOutboundAgentStore outboundAgentStore;
    private final FederationOutboundEventBufferRegistry bufferRegistry;
    private final FederationPeerAuthService authService;
    private final long staleAfterMs;
    private final boolean pollEnabled;
    private final Map<UUID, ProxySnapshot> proxySnapshots = new ConcurrentHashMap<>();
    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .build();

    public FederationPeerHealthService(
            FederationPeerStore peerStore,
            FederationTunnelHubService tunnelHubService,
            FederationOutboundAgentStore outboundAgentStore,
            FederationOutboundEventBufferRegistry bufferRegistry,
            @Lazy FederationPeerAuthService authService,
            @Value("${ispf.federation.health.stale-minutes:15}") long staleMinutes,
            @Value("${ispf.federation.health.poll-enabled:true}") boolean pollEnabled
    ) {
        this.peerStore = peerStore;
        this.tunnelHubService = tunnelHubService;
        this.outboundAgentStore = outboundAgentStore;
        this.bufferRegistry = bufferRegistry;
        this.authService = authService;
        this.staleAfterMs = Duration.ofMinutes(Math.max(1, staleMinutes)).toMillis();
        this.pollEnabled = pollEnabled;
    }

    /**
     * BL-188: active peer health poll for manager-of-managers hub — probes {@code GET /api/v1/info}
     * on each enabled peer so health badges stay fresh even without operator traffic.
     */
    @Scheduled(fixedDelayString = "${ispf.federation.health.poll-interval-ms:120000}")
    public void pollAllEnabledPeers() {
        if (!pollEnabled) {
            return;
        }
        for (FederationPeer peer : peerStore.listAll()) {
            if (!peer.enabled()) {
                continue;
            }
            try {
                pollPeer(peer);
            } catch (Exception ex) {
                log.debug("Federation peer health poll failed for {}: {}", peer.name(), ex.getMessage());
            }
        }
    }

    public void pollPeer(FederationPeer peer) {
        if (peer.isTunnelInbound()) {
            pollTunnelPeer(peer);
            return;
        }
        pollHttpPeer(peer, true);
    }

    private void pollTunnelPeer(FederationPeer peer) {
        if (!tunnelHubService.isConnected(peer.id())) {
            recordProxyFailure(peer.id(), "Tunnel offline");
            return;
        }
        try {
            tunnelHubService.dispatch(peer.id(), "GET", PROBE_PATH, null);
        } catch (ResponseStatusException ex) {
            if (ex.getStatusCode() != HttpStatus.SERVICE_UNAVAILABLE) {
                recordProxyFailure(peer.id(), ex.getReason() != null ? ex.getReason() : ex.getMessage());
            }
        }
    }

    private void pollHttpPeer(FederationPeer peer, boolean allowRefresh) {
        FederationPeer current = peerStore.findById(peer.id()).orElse(peer);
        String url = current.baseUrl().trim().replaceAll("/+$", "") + PROBE_PATH;
        long startedAt = System.nanoTime();
        try {
            HttpRequest.Builder builder = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .timeout(Duration.ofSeconds(10))
                    .GET();
            applyAuthorization(builder, current);
            HttpResponse<String> response = httpClient.send(builder.build(), HttpResponse.BodyHandlers.ofString());
            if ((response.statusCode() == 401 || response.statusCode() == 403)
                    && allowRefresh
                    && authService.refreshPeerIfUnauthorized(current.id())) {
                pollHttpPeer(peerStore.findById(current.id()).orElseThrow(), false);
                return;
            }
            if (response.statusCode() >= 200 && response.statusCode() < 300) {
                recordProxySuccess(current.id(), (System.nanoTime() - startedAt) / 1_000_000L);
            } else {
                recordProxyFailure(current.id(), "HTTP " + response.statusCode());
            }
        } catch (Exception ex) {
            recordProxyFailure(current.id(), ex.getMessage());
        }
    }

    private static void applyAuthorization(HttpRequest.Builder builder, FederationPeer peer) {
        if (peer.authToken() != null && !peer.authToken().isBlank()) {
            builder.header("Authorization", "Bearer " + peer.authToken().trim());
        }
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
