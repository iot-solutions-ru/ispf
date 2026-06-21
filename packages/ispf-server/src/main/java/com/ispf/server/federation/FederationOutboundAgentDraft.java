package com.ispf.server.federation;

import java.util.UUID;

public record FederationOutboundAgentDraft(
        String name,
        String hubBaseUrl,
        String registrationCodeEnc,
        String sessionTokenEnc,
        String pathPrefix,
        boolean enabled,
        UUID linkedPeerId
) {
}
