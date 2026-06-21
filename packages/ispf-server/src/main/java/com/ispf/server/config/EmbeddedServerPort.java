package com.ispf.server.config;

import org.springframework.boot.web.server.context.WebServerInitializedEvent;
import org.springframework.context.event.EventListener;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

/**
 * Resolves the actual HTTP listen port (including random test ports).
 */
@Component
public class EmbeddedServerPort {

    private volatile int port = -1;

    @EventListener
    public void onWebServerReady(WebServerInitializedEvent event) {
        port = event.getWebServer().getPort();
    }

    public int get(Environment environment) {
        if (port > 0) {
            return port;
        }
        Integer localPort = environment.getProperty("local.server.port", Integer.class);
        if (localPort != null && localPort > 0) {
            return localPort;
        }
        return environment.getProperty("server.port", Integer.class, 8080);
    }
}
