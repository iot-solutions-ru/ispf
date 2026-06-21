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
        FederationConnectionMode connectionMode,
        FederationAuthMode authMode,
        Instant tokenExpiresAt,
        String authUsername,
        String authSecretEnc,
        FederationAuthStatus authStatus,
        Instant lastAuthAt,
        String lastAuthError,
        Instant createdAt,
        Instant updatedAt
) {
    public boolean isTunnelInbound() {
        return connectionMode == FederationConnectionMode.TUNNEL_INBOUND;
    }

    public boolean hasServiceAccount() {
        return authMode == FederationAuthMode.SERVICE_ACCOUNT
                && authUsername != null && !authUsername.isBlank()
                && authSecretEnc != null && !authSecretEnc.isBlank();
    }
}
