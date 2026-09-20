package com.ispf.server.security;

import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class OutboundUrlSafetyTest {

    @Test
    void acceptsHttpsHostWhenLoopbackAllowed() {
        var uri = OutboundUrlSafety.requireSafeHttpUrl("https://peer.example.com/api", "", false);
        assertThat(uri.getHost()).isEqualTo("peer.example.com");
    }

    @Test
    void rejectsNonHttpScheme() {
        assertThatThrownBy(() -> OutboundUrlSafety.requireSafeHttpUrl("file:///etc/passwd", "", false))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("http or https");
    }

    @Test
    void rejectsCloudMetadataHost() {
        assertThatThrownBy(() ->
                OutboundUrlSafety.requireSafeHttpUrl("http://metadata.google.internal/latest", "", false))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("blocked");
    }

    @Test
    void rejectsLoopbackWhenBlocked() {
        assertThatThrownBy(() -> OutboundUrlSafety.requireSafeHttpUrl("http://127.0.0.1:8080", "", true))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("blocked");
    }

    @Test
    void allowsLoopbackWhenNotBlocked() {
        var uri = OutboundUrlSafety.requireSafeHttpUrl("http://127.0.0.1:8080", "", false);
        assertThat(uri.getHost()).isEqualTo("127.0.0.1");
    }

    @Test
    void enforcesAllowlist() {
        assertThatThrownBy(() ->
                OutboundUrlSafety.requireSafeHttpUrl("https://evil.example", "good.example,*.trusted.com", false))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("allowlist");
        var uri = OutboundUrlSafety.requireSafeHttpUrl("https://a.trusted.com", "good.example,*.trusted.com", false);
        assertThat(uri.getHost()).isEqualTo("a.trusted.com");
    }

    @Test
    void stripTrailingSlashesWithoutRegex() {
        assertThat(OutboundUrlSafety.stripTrailingSlashes("https://peer.example/")).isEqualTo("https://peer.example");
        assertThat(OutboundUrlSafety.stripTrailingSlashes("https://peer.example///")).isEqualTo("https://peer.example");
        assertThat(OutboundUrlSafety.stripTrailingSlashes("https://peer.example")).isEqualTo("https://peer.example");
    }

    @Test
    void resolvePathKeepsValidatedBase() {
        var base = OutboundUrlSafety.requireSafeHttpUrl("https://peer.example.com/ispf", "", false);
        var login = OutboundUrlSafety.resolvePath(base, "api/v1/auth/login");
        assertThat(login.toString()).isEqualTo("https://peer.example.com/ispf/api/v1/auth/login");
        var withQuery = OutboundUrlSafety.resolvePath(base, "/api/v1/objects?path=root");
        assertThat(withQuery.toString()).isEqualTo("https://peer.example.com/ispf/api/v1/objects?path=root");
    }
}
