package com.ispf.server.config;

import com.ispf.server.websocket.ObjectWebSocketHandler;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;

@Configuration
@EnableWebSocket
public class WebSocketConfig implements WebSocketConfigurer {

    private final ObjectWebSocketHandler objectWebSocketHandler;

    public WebSocketConfig(ObjectWebSocketHandler objectWebSocketHandler) {
        this.objectWebSocketHandler = objectWebSocketHandler;
    }

    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        registry.addHandler(objectWebSocketHandler, "/ws/objects")
                .setAllowedOrigins("*");
    }
}
