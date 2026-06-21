package com.ispf.server.federation;

import java.time.Instant;
import java.util.UUID;

public record FederationOutboundAgent(
        UUID id,
        String name,
        String hubBaseUrl,
        String registrationCodeEnc,
        String sessionTokenEnc,
        String pathPrefix,
        boolean enabled,
        FederationTunnelStatus tunnelStatus,
        UUID linkedPeerId,
        String lastError,
        Instant lastConnectedAt,
        Instant createdAt,
        Instant updatedAt
) {
}
