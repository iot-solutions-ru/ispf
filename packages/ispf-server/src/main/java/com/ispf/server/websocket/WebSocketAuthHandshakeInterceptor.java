package com.ispf.server.websocket;

import com.ispf.server.config.IspfRoles;
import com.ispf.server.config.IspfSecurityProperties;
import com.ispf.server.config.KeycloakJwtRoleConverter;
import com.ispf.server.tenant.TenantStore;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.http.server.ServletServerHttpRequest;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.server.HandshakeInterceptor;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Component
public class WebSocketAuthHandshakeInterceptor implements HandshakeInterceptor {

    /** Session attribute holding the handshake-authenticated {@link Authentication} (ACL checks). */
    public static final String AUTHENTICATION_ATTRIBUTE = "ispf.ws.authentication";

    private final ObjectProvider<PlatformTokenAuthenticator> platformTokenAuthenticator;
    private final TenantStore tenantStore;
    private final IspfSecurityProperties securityProperties;
    private final ObjectProvider<JwtDecoder> jwtDecoder;
    private final KeycloakJwtRoleConverter roleConverter = new KeycloakJwtRoleConverter();

    public WebSocketAuthHandshakeInterceptor(
            ObjectProvider<PlatformTokenAuthenticator> platformTokenAuthenticator,
            TenantStore tenantStore,
            IspfSecurityProperties securityProperties,
            ObjectProvider<JwtDecoder> jwtDecoder
    ) {
        this.platformTokenAuthenticator = platformTokenAuthenticator;
        this.tenantStore = tenantStore;
        this.securityProperties = securityProperties;
        this.jwtDecoder = jwtDecoder;
    }

    @Override
    public boolean beforeHandshake(
            ServerHttpRequest request,
            ServerHttpResponse response,
            WebSocketHandler wsHandler,
            Map<String, Object> attributes
    ) {
        if (!securityProperties.isRbacEnabled()) {
            return true;
        }
        if (!(request instanceof ServletServerHttpRequest servletRequest)) {
            return false;
        }
        HttpServletRequest httpRequest = servletRequest.getServletRequest();
        String token = WebSocketAuthSupport.extractToken(request, httpRequest);
        if (token == null || token.isBlank()) {
            return false;
        }
        boolean viaSubprotocol = WebSocketAuthSupport.usedSubprotocolToken(request);
        boolean authenticated = authenticatePlatformToken(token, attributes) || authenticateJwt(token, attributes);
        if (authenticated && viaSubprotocol) {
            response.getHeaders().set(WebSocketAuthSupport.SEC_WEBSOCKET_PROTOCOL, WebSocketAuthSupport.BEARER_PROTOCOL);
        }
        return authenticated;
    }

    /**
     * Platform session tokens are honored only when the local/test-profile authenticator bean
     * is present; the prod (keycloak) chain accepts JWTs only, exactly like the HTTP filter chain.
     */
    private boolean authenticatePlatformToken(String token, Map<String, Object> attributes) {
        PlatformTokenAuthenticator authenticator = platformTokenAuthenticator.getIfAvailable();
        if (authenticator == null) {
            return false;
        }
        return authenticator.authenticate(token)
                .map(user -> {
                    attributes.put("username", user.username());
                    attributes.put("roles", user.roles());
                    putTenantId(attributes, user.username(), user.roles());
                    // Mirror LocalBearerTokenFilter: any authenticated local user gets operator baseline.
                    Set<String> resolvedRoles = new LinkedHashSet<>(user.roles());
                    if (!user.roles().isEmpty()) {
                        resolvedRoles.add(IspfRoles.OPERATOR);
                    }
                    putAuthentication(attributes, user.username(), resolvedRoles);
                    return true;
                })
                .orElse(false);
    }

    private boolean authenticateJwt(String token, Map<String, Object> attributes) {
        JwtDecoder decoder = jwtDecoder.getIfAvailable();
        if (decoder == null) {
            return false;
        }
        try {
            Jwt jwt = decoder.decode(token);
            String username = jwt.getClaimAsString("preferred_username");
            if (username == null || username.isBlank()) {
                username = jwt.getSubject();
            }
            if (username == null || username.isBlank()) {
                return false;
            }
            Collection<GrantedAuthority> authorities = roleConverter.convert(jwt);
            List<String> roles = new ArrayList<>();
            for (GrantedAuthority authority : authorities) {
                String value = authority.getAuthority();
                if (value.startsWith("ROLE_")) {
                    value = value.substring("ROLE_".length());
                }
                if (IspfRoles.ADMIN.equals(value)
                        || IspfRoles.DEVELOPER.equals(value)
                        || IspfRoles.OPERATOR.equals(value)) {
                    roles.add(value);
                }
            }
            if (roles.isEmpty()) {
                return false;
            }
            attributes.put("username", username);
            attributes.put("roles", roles);
            putTenantId(attributes, username, roles);
            putAuthentication(attributes, username, roles);
            return true;
        } catch (JwtException ex) {
            return false;
        }
    }

    private static void putAuthentication(Map<String, Object> attributes, String username, Collection<String> roles) {
        List<SimpleGrantedAuthority> authorities = roles.stream()
                .map(role -> new SimpleGrantedAuthority("ROLE_" + role))
                .toList();
        attributes.put(
                AUTHENTICATION_ATTRIBUTE,
                new UsernamePasswordAuthenticationToken(username, null, authorities)
        );
    }

    private void putTenantId(Map<String, Object> attributes, String username, List<String> roles) {
        if (roles != null && roles.stream().anyMatch(IspfRoles.ADMIN::equalsIgnoreCase)) {
            return;
        }
        tenantStore.findTenantIdForUser(username).ifPresent(tenantId -> attributes.put("tenantId", tenantId));
    }

    @Override
    public void afterHandshake(
            ServerHttpRequest request,
            ServerHttpResponse response,
            WebSocketHandler wsHandler,
            Exception exception
    ) {
        // no-op
    }
}
