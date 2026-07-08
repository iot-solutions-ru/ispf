package com.ispf.server.websocket;

import com.ispf.server.config.IspfRoles;
import com.ispf.server.config.IspfSecurityProperties;
import com.ispf.server.config.KeycloakJwtRoleConverter;
import com.ispf.server.security.PlatformUserService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.http.server.ServletServerHttpRequest;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.server.HandshakeInterceptor;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;

@Component
public class WebSocketAuthHandshakeInterceptor implements HandshakeInterceptor {

    private final PlatformUserService userService;
    private final IspfSecurityProperties securityProperties;
    private final ObjectProvider<JwtDecoder> jwtDecoder;
    private final KeycloakJwtRoleConverter roleConverter = new KeycloakJwtRoleConverter();

    public WebSocketAuthHandshakeInterceptor(
            PlatformUserService userService,
            IspfSecurityProperties securityProperties,
            ObjectProvider<JwtDecoder> jwtDecoder
    ) {
        this.userService = userService;
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
        boolean authenticated = userService.authenticateToken(token)
                .map(user -> {
                    attributes.put("username", user.username());
                    attributes.put("roles", user.roles());
                    return true;
                })
                .orElseGet(() -> authenticateJwt(token, attributes));
        if (authenticated && viaSubprotocol) {
            response.getHeaders().set(WebSocketAuthSupport.SEC_WEBSOCKET_PROTOCOL, WebSocketAuthSupport.BEARER_PROTOCOL);
        }
        return authenticated;
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
            return true;
        } catch (JwtException ex) {
            return false;
        }
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
