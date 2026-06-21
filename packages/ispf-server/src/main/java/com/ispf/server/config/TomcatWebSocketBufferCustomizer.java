package com.ispf.server.config;

import jakarta.websocket.server.ServerContainer;
import org.apache.catalina.Context;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.tomcat.TomcatWebServer;
import org.springframework.boot.web.server.WebServer;
import org.springframework.boot.web.server.context.WebServerInitializedEvent;
import org.springframework.context.ApplicationListener;
import org.springframework.stereotype.Component;

@Component
public class TomcatWebSocketBufferCustomizer implements ApplicationListener<WebServerInitializedEvent> {

    private static final Logger log = LoggerFactory.getLogger(TomcatWebSocketBufferCustomizer.class);
    private static final int BUFFER_SIZE = 4 * 1024 * 1024;

    @Override
    public void onApplicationEvent(WebServerInitializedEvent event) {
        WebServer webServer = event.getWebServer();
        if (!(webServer instanceof TomcatWebServer tomcatWebServer)) {
            return;
        }
        try {
            var host = tomcatWebServer.getTomcat().getHost();
            for (var child : host.findChildren()) {
                if (!(child instanceof Context context)) {
                    continue;
                }
                var servletContext = context.getServletContext();
                if (servletContext == null) {
                    continue;
                }
                Object container = servletContext.getAttribute(ServerContainer.class.getName());
                if (container instanceof ServerContainer serverContainer) {
                    serverContainer.setDefaultMaxTextMessageBufferSize(BUFFER_SIZE);
                    serverContainer.setDefaultMaxBinaryMessageBufferSize(BUFFER_SIZE);
                    log.info("Configured Tomcat WebSocket buffers to {} bytes", BUFFER_SIZE);
                    return;
                }
            }
            log.warn("Tomcat WebSocket ServerContainer not found; federation tunnel catalog sync may fail");
        } catch (Exception e) {
            log.warn("Failed to configure Tomcat WebSocket buffers: {}", e.getMessage());
        }
    }
}
