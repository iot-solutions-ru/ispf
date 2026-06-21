package com.ispf.server.federation;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.http.server.ServletServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.server.HandshakeInterceptor;

import java.util.Map;
import java.util.UUID;

@Component
public class FederationTunnelHandshakeInterceptor implements HandshakeInterceptor {

    @Override
    public boolean beforeHandshake(
            ServerHttpRequest request,
            ServerHttpResponse response,
            WebSocketHandler wsHandler,
            Map<String, Object> attributes
    ) {
        if (!(request instanceof ServletServerHttpRequest servletRequest)) {
            return false;
        }
        HttpServletRequest httpRequest = servletRequest.getServletRequest();
        String registrationCode = trim(httpRequest.getParameter("registrationCode"));
        String sessionToken = trim(httpRequest.getParameter("sessionToken"));
        String peerIdRaw = trim(httpRequest.getParameter("peerId"));
        String siteName = trim(httpRequest.getParameter("siteName"));
        String pathPrefix = trim(httpRequest.getParameter("pathPrefix"));

        if (registrationCode != null) {
            if (siteName == null) {
                return false;
            }
            attributes.put("tunnelMode", "registration");
            attributes.put("registrationCode", registrationCode);
            attributes.put("siteName", siteName);
            if (pathPrefix != null) {
                attributes.put("pathPrefix", pathPrefix);
            }
            return true;
        }

        if (sessionToken != null && peerIdRaw != null) {
            attributes.put("tunnelMode", "reconnect");
            attributes.put("sessionToken", sessionToken);
            attributes.put("peerId", UUID.fromString(peerIdRaw));
            return true;
        }

        return false;
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

    private static String trim(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }
}
