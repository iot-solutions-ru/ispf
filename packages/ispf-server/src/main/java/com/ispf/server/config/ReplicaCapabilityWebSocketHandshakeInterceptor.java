package com.ispf.server.config;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.server.HandshakeInterceptor;

import java.util.Map;

/**
 * ADR-0032: reject /ws/objects when ws capability is disabled.
 */
@Component
public class ReplicaCapabilityWebSocketHandshakeInterceptor implements HandshakeInterceptor {

    private final ClusterProperties clusterProperties;

    public ReplicaCapabilityWebSocketHandshakeInterceptor(ClusterProperties clusterProperties) {
        this.clusterProperties = clusterProperties;
    }

    @Override
    public boolean beforeHandshake(
            org.springframework.http.server.ServerHttpRequest request,
            org.springframework.http.server.ServerHttpResponse response,
            org.springframework.web.socket.WebSocketHandler wsHandler,
            Map<String, Object> attributes
    ) {
        if (clusterProperties.enabled() && !clusterProperties.isWsActive()) {
            if (response instanceof org.springframework.http.server.ServletServerHttpResponse servletResponse) {
                servletResponse.setStatusCode(HttpStatus.SERVICE_UNAVAILABLE);
            }
            return false;
        }
        return true;
    }

    @Override
    public void afterHandshake(
            org.springframework.http.server.ServerHttpRequest request,
            org.springframework.http.server.ServerHttpResponse response,
            org.springframework.web.socket.WebSocketHandler wsHandler,
            Exception exception
    ) {
        // no-op
    }
}
