package com.ispf.server.federation;

import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class FederationTunnelProtocolTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void proxyRequestIncludesDelegatedPrincipal() throws Exception {
        String payload = FederationTunnelProtocol.proxyRequest(
                "request-1",
                "GET",
                "/api/v1/objects/by-path/variables",
                "path=root.platform.devices.pump1",
                null,
                "alice",
                List.of("ROLE_operator", "developer"),
                "tenant-a",
                objectMapper
        );

        var node = objectMapper.readTree(payload);
        assertThat(node.path("onBehalfOfUser").asString()).isEqualTo("alice");
        assertThat(node.path("onBehalfOfRoles").size()).isEqualTo(2);
        assertThat(node.path("onBehalfOfRoles").get(0).asString()).isEqualTo("operator");
        assertThat(node.path("onBehalfOfRoles").get(1).asString()).isEqualTo("developer");
        assertThat(node.path("onBehalfOfTenant").asString()).isEqualTo("tenant-a");
    }

    @Test
    void legacyProxyRequestOmitsDelegatedPrincipal() throws Exception {
        String payload = FederationTunnelProtocol.proxyRequest(
                "request-1",
                "GET",
                "/api/v1/objects",
                null,
                null,
                objectMapper
        );

        var node = objectMapper.readTree(payload);
        assertThat(node.has("onBehalfOfUser")).isFalse();
        assertThat(node.has("onBehalfOfRoles")).isFalse();
        assertThat(node.has("onBehalfOfTenant")).isFalse();
    }
}
