package com.ispf.server.federation;

import java.time.Instant;
import java.util.UUID;

public record FederationPeerHealth(
        UUID peerId,
        FederationPeerHealthLevel level,
        boolean tunnelConnected,
        Instant lastSuccessfulProxyAt,
        Long lastProxyLatencyMs,
        String lastProxyError,
        int pendingBufferedEvents,
        String summary
) {
}
