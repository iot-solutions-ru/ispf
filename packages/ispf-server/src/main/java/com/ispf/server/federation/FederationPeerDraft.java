package com.ispf.server.federation;

public record FederationPeerDraft(
        String name,
        String baseUrl,
        String authToken,
        String pathPrefix,
        boolean enabled,
        String description
) {
}
