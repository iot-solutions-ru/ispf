package com.ispf.server.websocket;

import com.ispf.server.config.IspfSecurityProperties;
import com.ispf.server.security.PlatformUserService;
import com.ispf.server.tenant.TenantStore;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.http.HttpHeaders;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.http.server.ServletServerHttpRequest;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.web.socket.WebSocketHandler;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class WebSocketAuthHandshakeInterceptorTest {

    private PlatformTokenAuthenticator platformTokenAuthenticator;
    private ObjectProvider<PlatformTokenAuthenticator> platformAuthenticatorProvider;
    private ObjectProvider<JwtDecoder> jwtDecoderProvider;
    private JwtDecoder jwtDecoder;
    private WebSocketAuthHandshakeInterceptor interceptor;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        platformTokenAuthenticator = mock(PlatformTokenAuthenticator.class);
        platformAuthenticatorProvider = mock(ObjectProvider.class);
        jwtDecoder = mock(JwtDecoder.class);
        jwtDecoderProvider = mock(ObjectProvider.class);
        TenantStore tenantStore = mock(TenantStore.class);
        when(tenantStore.findTenantIdForUser(anyString())).thenReturn(Optional.empty());
        interceptor = new WebSocketAuthHandshakeInterceptor(
                platformAuthenticatorProvider,
                tenantStore,
                new IspfSecurityProperties(),
                jwtDecoderProvider
        );
    }

    @Test
    void localProfileAcceptsPlatformSessionToken() {
        when(platformAuthenticatorProvider.getIfAvailable()).thenReturn(platformTokenAuthenticator);
        when(platformTokenAuthenticator.authenticate("tok")).thenReturn(Optional.of(
                new PlatformUserService.AuthenticatedUser("admin", "Administrator", List.of("admin"))
        ));

        Map<String, Object> attributes = new HashMap<>();
        boolean accepted = interceptor.beforeHandshake(request("Bearer tok"), response(), mock(WebSocketHandler.class), attributes);

        assertThat(accepted).isTrue();
        assertThat(attributes.get("username")).isEqualTo("admin");
        assertThat(attributes.get("roles")).isEqualTo(List.of("admin"));
        assertThat(attributes.get(WebSocketAuthHandshakeInterceptor.AUTHENTICATION_ATTRIBUTE))
                .isInstanceOf(Authentication.class);
    }

    @Test
    void prodProfileIgnoresPlatformSessionToken() {
        // No PlatformTokenAuthenticator bean outside local/test profiles — platform tokens must fail.
        when(platformAuthenticatorProvider.getIfAvailable()).thenReturn(null);
        when(jwtDecoderProvider.getIfAvailable()).thenReturn(null);

        Map<String, Object> attributes = new HashMap<>();
        boolean accepted = interceptor.beforeHandshake(request("Bearer tok"), response(), mock(WebSocketHandler.class), attributes);

        assertThat(accepted).isFalse();
        assertThat(attributes).isEmpty();
    }

    @Test
    void prodProfileStillAcceptsJwt() {
        when(platformAuthenticatorProvider.getIfAvailable()).thenReturn(null);
        when(jwtDecoderProvider.getIfAvailable()).thenReturn(jwtDecoder);
        String jwtToken = "header.payload.sig";
        Jwt jwt = Jwt.withTokenValue(jwtToken)
                .header("alg", "none")
                .claim("preferred_username", "kc-user")
                .claim("realm_access", Map.of("roles", List.of("operator")))
                .build();
        when(jwtDecoder.decode(jwtToken)).thenReturn(jwt);

        Map<String, Object> attributes = new HashMap<>();
        boolean accepted = interceptor.beforeHandshake(request("Bearer " + jwtToken), response(), mock(WebSocketHandler.class), attributes);

        assertThat(accepted).isTrue();
        assertThat(attributes.get("username")).isEqualTo("kc-user");
        assertThat(attributes.get("roles")).isEqualTo(List.of("operator"));
        assertThat(attributes.get(WebSocketAuthHandshakeInterceptor.AUTHENTICATION_ATTRIBUTE))
                .isInstanceOf(Authentication.class);
    }

    @Test
    void jwtDecoderResolutionFailureDoesNotFailHandshakeHard() {
        // Local demostand: platform token rejected (expired/unknown), then JWT path would hit
        // SupplierJwtDecoder → UnknownHostException for issuer example.invalid.
        when(platformAuthenticatorProvider.getIfAvailable()).thenReturn(platformTokenAuthenticator);
        when(platformTokenAuthenticator.authenticate("header.payload.sig")).thenReturn(Optional.empty());
        when(jwtDecoderProvider.getIfAvailable()).thenReturn(jwtDecoder);
        when(jwtDecoder.decode("header.payload.sig")).thenThrow(
                new IllegalStateException("Failed to lazily resolve the supplied JwtDecoder instance",
                        new java.net.UnknownHostException("example.invalid"))
        );

        Map<String, Object> attributes = new HashMap<>();
        boolean accepted = interceptor.beforeHandshake(
                request("Bearer header.payload.sig"),
                response(),
                mock(WebSocketHandler.class),
                attributes
        );

        assertThat(accepted).isFalse();
        assertThat(attributes).isEmpty();
    }

    @Test
    void opaquePlatformTokenSkipsBrokenJwtDecoder() {
        when(platformAuthenticatorProvider.getIfAvailable()).thenReturn(platformTokenAuthenticator);
        when(platformTokenAuthenticator.authenticate("deadbeefcafe")).thenReturn(Optional.empty());
        // JwtDecoder must not be consulted for opaque tokens (would DNS-fail on example.invalid).
        when(jwtDecoderProvider.getIfAvailable()).thenReturn(jwtDecoder);

        Map<String, Object> attributes = new HashMap<>();
        boolean accepted = interceptor.beforeHandshake(
                request("Bearer deadbeefcafe"),
                response(),
                mock(WebSocketHandler.class),
                attributes
        );

        assertThat(accepted).isFalse();
        assertThat(attributes).isEmpty();
        org.mockito.Mockito.verify(jwtDecoder, org.mockito.Mockito.never()).decode(anyString());
    }

    @Test
    void looksLikeJwtRequiresThreeSegments() {
        assertThat(WebSocketAuthHandshakeInterceptor.looksLikeJwt("a.b.c")).isTrue();
        assertThat(WebSocketAuthHandshakeInterceptor.looksLikeJwt("deadbeef")).isFalse();
        assertThat(WebSocketAuthHandshakeInterceptor.looksLikeJwt("a.b")).isFalse();
        assertThat(WebSocketAuthHandshakeInterceptor.looksLikeJwt("a.b.c.d")).isFalse();
        assertThat(WebSocketAuthHandshakeInterceptor.looksLikeJwt(null)).isFalse();
    }

    private static ServletServerHttpRequest request(String authorization) {
        HttpServletRequest servletRequest = mock(HttpServletRequest.class);
        when(servletRequest.getHeader("Authorization")).thenReturn(authorization);
        when(servletRequest.getHeaderNames()).thenReturn(java.util.Collections.emptyEnumeration());
        when(servletRequest.getHeaders(org.mockito.ArgumentMatchers.anyString()))
                .thenReturn(java.util.Collections.emptyEnumeration());
        return new ServletServerHttpRequest(servletRequest);
    }

    private static ServerHttpResponse response() {
        ServerHttpResponse response = mock(ServerHttpResponse.class);
        when(response.getHeaders()).thenReturn(new HttpHeaders());
        return response;
    }
}
