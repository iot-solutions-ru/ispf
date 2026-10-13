package com.ispf.server.security;

import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;

import java.net.URI;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class OutboundUrlSafetyPublicHttpsTest {

    @Test
    void requireSafePublicHttpsUrlRejectsHttpAndPrivateLiterals() {
        assertThatThrownBy(() -> OutboundUrlSafety.requireSafePublicHttpsUrl("http://example.com/x"))
                .isInstanceOf(ResponseStatusException.class);
        assertThatThrownBy(() -> OutboundUrlSafety.requireSafePublicHttpsUrl("https://10.0.0.1/x"))
                .isInstanceOf(ResponseStatusException.class);
        assertThatThrownBy(() -> OutboundUrlSafety.requireSafePublicHttpsUrl("https://192.168.1.1/x"))
                .isInstanceOf(ResponseStatusException.class);
        assertThatThrownBy(() -> OutboundUrlSafety.requireSafePublicHttpsUrl("https://172.16.0.1/x"))
                .isInstanceOf(ResponseStatusException.class);
        assertThatThrownBy(() -> OutboundUrlSafety.requireSafePublicHttpsUrl("https://localhost/x"))
                .isInstanceOf(ResponseStatusException.class);
    }

    @Test
    void resolveEncodedPathKeepsPercentEncoding() {
        URI base = OutboundUrlSafety.requireSafePublicHttpsUrl("https://gitlab.com/api/v4");
        URI resolved = OutboundUrlSafety.resolveEncodedPath(
                base,
                "projects/group%2Fproject/repository/files/path%2Fbundle.json?ref=release%2F1.0"
        );
        assertThat(resolved.toString()).contains("projects/group%2Fproject/");
        assertThat(resolved.toString()).contains("files/path%2Fbundle.json");
        assertThat(resolved.toString()).contains("ref=release%2F1.0");
        assertThat(resolved.toString()).doesNotContain("%252F");
    }
}
