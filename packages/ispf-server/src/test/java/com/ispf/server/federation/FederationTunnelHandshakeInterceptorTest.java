package com.ispf.server.federation;

import org.junit.jupiter.api.Test;
import org.springframework.http.server.ServletServerHttpRequest;
import org.springframework.http.server.ServletServerHttpResponse;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.web.socket.WebSocketHandler;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class FederationTunnelHandshakeInterceptorTest {

    private final FederationTunnelHandshakeInterceptor interceptor = new FederationTunnelHandshakeInterceptor();

    @Test
    void prefersHeadersOverQuery() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/ws/federation/tunnel");
        request.addHeader(FederationTunnelHandshakeInterceptor.HEADER_REGISTRATION_CODE, "header-code");
        request.addHeader(FederationTunnelHandshakeInterceptor.HEADER_SITE_NAME, "site-a");
        request.addHeader(FederationTunnelHandshakeInterceptor.HEADER_PATH_PREFIX, "root.platform");
        request.setParameter("registrationCode", "query-code");
        request.setParameter("siteName", "query-site");

        Map<String, Object> attributes = new HashMap<>();
        boolean ok = interceptor.beforeHandshake(
                new ServletServerHttpRequest(request),
                new ServletServerHttpResponse(new MockHttpServletResponse()),
                mock(WebSocketHandler.class),
                attributes
        );

        assertThat(ok).isTrue();
        assertThat(attributes.get("registrationCode")).isEqualTo("header-code");
        assertThat(attributes.get("siteName")).isEqualTo("site-a");
        assertThat(attributes.get("pathPrefix")).isEqualTo("root.platform");
    }

    @Test
    void acceptsQueryFallbackForCompat() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/ws/federation/tunnel");
        UUID peerId = UUID.randomUUID();
        request.setParameter("sessionToken", "tok");
        request.setParameter("peerId", peerId.toString());

        Map<String, Object> attributes = new HashMap<>();
        boolean ok = interceptor.beforeHandshake(
                new ServletServerHttpRequest(request),
                new ServletServerHttpResponse(new MockHttpServletResponse()),
                mock(WebSocketHandler.class),
                attributes
        );

        assertThat(ok).isTrue();
        assertThat(attributes.get("sessionToken")).isEqualTo("tok");
        assertThat(attributes.get("peerId")).isEqualTo(peerId);
    }
}
