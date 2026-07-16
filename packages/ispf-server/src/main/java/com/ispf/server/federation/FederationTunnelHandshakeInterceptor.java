package com.ispf.server.federation;

import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.http.server.ServletServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.server.HandshakeInterceptor;

import java.util.Map;
import java.util.UUID;

/**
 * Federation tunnel auth. Prefer headers ({@code X-ISPF-*}); query parameters remain
 * for backward compatibility but are discouraged (appear in logs/proxies).
 */
@Component
public class FederationTunnelHandshakeInterceptor implements HandshakeInterceptor {

    private static final Logger log = LoggerFactory.getLogger(FederationTunnelHandshakeInterceptor.class);

    static final String HEADER_REGISTRATION_CODE = "X-ISPF-Registration-Code";
    static final String HEADER_SESSION_TOKEN = "X-ISPF-Session-Token";
    static final String HEADER_PEER_ID = "X-ISPF-Peer-Id";
    static final String HEADER_SITE_NAME = "X-ISPF-Site-Name";
    static final String HEADER_PATH_PREFIX = "X-ISPF-Path-Prefix";

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
        String registrationCode = firstNonBlank(
                header(httpRequest, HEADER_REGISTRATION_CODE),
                query(httpRequest, "registrationCode")
        );
        String sessionToken = firstNonBlank(
                header(httpRequest, HEADER_SESSION_TOKEN),
                query(httpRequest, "sessionToken")
        );
        String peerIdRaw = firstNonBlank(
                header(httpRequest, HEADER_PEER_ID),
                query(httpRequest, "peerId")
        );
        String siteName = firstNonBlank(
                header(httpRequest, HEADER_SITE_NAME),
                query(httpRequest, "siteName")
        );
        String pathPrefix = firstNonBlank(
                header(httpRequest, HEADER_PATH_PREFIX),
                query(httpRequest, "pathPrefix")
        );

        boolean usedQuerySecrets = hasQuery(httpRequest, "registrationCode") || hasQuery(httpRequest, "sessionToken");
        if (usedQuerySecrets) {
            log.debug("Federation tunnel handshake used query-string secrets; prefer X-ISPF-* headers");
        }

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

    private static String header(HttpServletRequest request, String name) {
        return trim(request.getHeader(name));
    }

    private static String query(HttpServletRequest request, String name) {
        return trim(request.getParameter(name));
    }

    private static boolean hasQuery(HttpServletRequest request, String name) {
        return trim(request.getParameter(name)) != null;
    }

    private static String firstNonBlank(String primary, String fallback) {
        return primary != null ? primary : fallback;
    }

    private static String trim(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }
}
