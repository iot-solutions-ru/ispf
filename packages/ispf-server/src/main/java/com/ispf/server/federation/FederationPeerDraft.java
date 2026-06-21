package com.ispf.server.federation;

import java.time.Instant;

public record FederationPeerDraft(
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
        String authPasswordPlain,
        String authSecretEnc,
        FederationAuthStatus authStatus,
        Instant lastAuthAt,
        String lastAuthError
) {
    public static FederationPeerDraft httpPeer(
            String name,
            String baseUrl,
            String authToken,
            String pathPrefix,
            boolean enabled,
            String description
    ) {
        return new FederationPeerDraft(
                name,
                baseUrl,
                authToken,
                pathPrefix,
                enabled,
                description,
                FederationConnectionMode.HTTP_PULL,
                FederationAuthMode.STATIC_TOKEN,
                null,
                null,
                null,
                null,
                FederationAuthStatus.OK,
                null,
                null
        );
    }

    public static FederationPeerDraft tunnelPeer(
            String name,
            String pathPrefix,
            String description
    ) {
        return new FederationPeerDraft(
                name,
                FederationPeerStore.tunnelBaseUrl(name),
                null,
                pathPrefix,
                true,
                description,
                FederationConnectionMode.TUNNEL_INBOUND,
                FederationAuthMode.STATIC_TOKEN,
                null,
                null,
                null,
                null,
                FederationAuthStatus.OK,
                null,
                null
        );
    }
}
