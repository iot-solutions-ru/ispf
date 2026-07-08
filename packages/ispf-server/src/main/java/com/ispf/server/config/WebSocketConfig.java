package com.ispf.server.config;

import com.ispf.server.federation.FederationTunnelHandshakeInterceptor;
import com.ispf.server.federation.FederationTunnelWebSocketHandler;
import com.ispf.server.websocket.ObjectWebSocketHandler;
import com.ispf.server.websocket.WebSocketAuthHandshakeInterceptor;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;

@Configuration
@EnableWebSocket
@EnableConfigurationProperties(WebSocketProperties.class)
public class WebSocketConfig implements WebSocketConfigurer {

    private final ObjectWebSocketHandler objectWebSocketHandler;
    private final ReplicaCapabilityWebSocketHandshakeInterceptor capabilityHandshakeInterceptor;
    private final WebSocketAuthHandshakeInterceptor authHandshakeInterceptor;
    private final FederationTunnelWebSocketHandler federationTunnelWebSocketHandler;
    private final FederationTunnelHandshakeInterceptor federationTunnelHandshakeInterceptor;
    private final WebSocketProperties webSocketProperties;

    public WebSocketConfig(
            ObjectWebSocketHandler objectWebSocketHandler,
            ReplicaCapabilityWebSocketHandshakeInterceptor capabilityHandshakeInterceptor,
            WebSocketAuthHandshakeInterceptor authHandshakeInterceptor,
            FederationTunnelWebSocketHandler federationTunnelWebSocketHandler,
            FederationTunnelHandshakeInterceptor federationTunnelHandshakeInterceptor,
            WebSocketProperties webSocketProperties
    ) {
        this.objectWebSocketHandler = objectWebSocketHandler;
        this.capabilityHandshakeInterceptor = capabilityHandshakeInterceptor;
        this.authHandshakeInterceptor = authHandshakeInterceptor;
        this.federationTunnelWebSocketHandler = federationTunnelWebSocketHandler;
        this.federationTunnelHandshakeInterceptor = federationTunnelHandshakeInterceptor;
        this.webSocketProperties = webSocketProperties;
    }

    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        String[] allowedOrigins = webSocketProperties.resolvedAllowedOriginPatterns();
        registry.addHandler(objectWebSocketHandler, "/ws/objects")
                .addInterceptors(capabilityHandshakeInterceptor, authHandshakeInterceptor)
                .setAllowedOriginPatterns(allowedOrigins);
        registry.addHandler(federationTunnelWebSocketHandler, "/ws/federation/tunnel")
                .addInterceptors(federationTunnelHandshakeInterceptor)
                .setAllowedOriginPatterns(allowedOrigins);
    }
}
