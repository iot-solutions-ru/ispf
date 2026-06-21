package com.ispf.server.config;

import jakarta.websocket.server.ServerContainer;
import org.springframework.boot.web.server.WebServer;
import org.springframework.boot.web.server.context.WebServerInitializedEvent;
import org.springframework.context.ApplicationListener;
import org.springframework.stereotype.Component;

@Component
public class TomcatWebSocketBufferCustomizer implements ApplicationListener<WebServerInitializedEvent> {

    private static final int BUFFER_SIZE = 1024 * 1024;

    @Override
    public void onApplicationEvent(WebServerInitializedEvent event) {
        WebServer webServer = event.getWebServer();
        try {
            var getTomcat = webServer.getClass().getMethod("getTomcat");
            Object tomcat = getTomcat.invoke(webServer);
            var host = tomcat.getClass().getMethod("getHost").invoke(tomcat);
            var findChildren = host.getClass().getMethod("findChildren");
            Object[] contexts = (Object[]) findChildren.invoke(host);
            for (Object context : contexts) {
                var getServletContext = context.getClass().getMethod("getServletContext");
                Object servletContext = getServletContext.invoke(context);
                var getAttribute = servletContext.getClass().getMethod("getAttribute", String.class);
                Object container = getAttribute.invoke(servletContext, ServerContainer.class.getName());
                if (container instanceof ServerContainer serverContainer) {
                    serverContainer.setDefaultMaxTextMessageBufferSize(BUFFER_SIZE);
                    serverContainer.setDefaultMaxBinaryMessageBufferSize(BUFFER_SIZE);
                }
            }
        } catch (ReflectiveOperationException ignored) {
            // non-Tomcat embedded server in tests — skip
        }
    }
}
