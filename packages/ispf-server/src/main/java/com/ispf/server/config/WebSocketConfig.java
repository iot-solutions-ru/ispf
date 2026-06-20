package com.ispf.server.config;

import com.ispf.server.websocket.ObjectWebSocketHandler;
import com.ispf.server.websocket.WebSocketAuthHandshakeInterceptor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;

@Configuration
@EnableWebSocket
public class WebSocketConfig implements WebSocketConfigurer {

    private final ObjectWebSocketHandler objectWebSocketHandler;
    private final WebSocketAuthHandshakeInterceptor authHandshakeInterceptor;

    public WebSocketConfig(
            ObjectWebSocketHandler objectWebSocketHandler,
            WebSocketAuthHandshakeInterceptor authHandshakeInterceptor
    ) {
        this.objectWebSocketHandler = objectWebSocketHandler;
        this.authHandshakeInterceptor = authHandshakeInterceptor;
    }

    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        registry.addHandler(objectWebSocketHandler, "/ws/objects")
                .addInterceptors(authHandshakeInterceptor)
                .setAllowedOrigins("*");
    }
}
