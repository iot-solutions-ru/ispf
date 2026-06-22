package com.ispf.server.websocket;

import com.ispf.server.config.IspfSecurityProperties;
import com.ispf.server.config.LocalBearerTokenFilter;
import com.ispf.server.security.PlatformUserService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.http.server.ServletServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.server.HandshakeInterceptor;

import java.util.Map;

@Component
public class WebSocketAuthHandshakeInterceptor implements HandshakeInterceptor {

    private final PlatformUserService userService;
    private final IspfSecurityProperties securityProperties;

    public WebSocketAuthHandshakeInterceptor(
            PlatformUserService userService,
            IspfSecurityProperties securityProperties
    ) {
        this.userService = userService;
        this.securityProperties = securityProperties;
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
        String token = httpRequest.getParameter("token");
        if (token == null || token.isBlank()) {
            String authorization = httpRequest.getHeader(LocalBearerTokenFilter.AUTHORIZATION_HEADER);
            if (authorization != null && authorization.startsWith("Bearer ")) {
                token = authorization.substring("Bearer ".length()).trim();
            }
        }
        if (token == null || token.isBlank()) {
            return false;
        }
        return userService.authenticateToken(token)
                .map(user -> {
                    attributes.put("username", user.username());
                    attributes.put("roles", user.roles());
                    return true;
                })
                .orElse(false);
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
