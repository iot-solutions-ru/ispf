package com.ispf.server.federation;

import java.time.Instant;
import java.util.UUID;

public record FederationPeer(
        UUID id,
        String name,
        String baseUrl,
        String authToken,
        String pathPrefix,
        boolean enabled,
        String description,
        Instant createdAt,
        Instant updatedAt
) {
}
