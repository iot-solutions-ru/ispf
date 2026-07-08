package com.ispf.server.websocket;

import org.springframework.http.HttpHeaders;
import org.springframework.http.server.ServerHttpRequest;

import java.util.List;

/**
 * Extracts bearer tokens for WebSocket handshakes without putting secrets in URL query strings.
 */
public final class WebSocketAuthSupport {

    public static final String BEARER_PROTOCOL = "ispf-bearer";
    public static final String SEC_WEBSOCKET_PROTOCOL = "Sec-WebSocket-Protocol";

    private WebSocketAuthSupport() {
    }

    public static String extractToken(ServerHttpRequest request, jakarta.servlet.http.HttpServletRequest httpRequest) {
        String fromAuthorization = bearerFromAuthorization(httpRequest.getHeader("Authorization"));
        if (fromAuthorization != null) {
            return fromAuthorization;
        }
        String fromSubprotocol = bearerFromSubprotocol(request.getHeaders().get(SEC_WEBSOCKET_PROTOCOL));
        if (fromSubprotocol != null) {
            return fromSubprotocol;
        }
        return null;
    }

    public static boolean usedSubprotocolToken(ServerHttpRequest request) {
        return bearerFromSubprotocol(request.getHeaders().get(SEC_WEBSOCKET_PROTOCOL)) != null;
    }

    public static String bearerFromAuthorization(String authorization) {
        if (authorization != null && authorization.startsWith("Bearer ")) {
            String token = authorization.substring("Bearer ".length()).trim();
            return token.isBlank() ? null : token;
        }
        return null;
    }

    public static String bearerFromSubprotocol(List<String> protocolHeaders) {
        if (protocolHeaders == null || protocolHeaders.isEmpty()) {
            return null;
        }
        for (String header : protocolHeaders) {
            if (header == null || header.isBlank()) {
                continue;
            }
            String[] parts = header.split(",");
            for (int i = 0; i < parts.length; i++) {
                if (BEARER_PROTOCOL.equals(parts[i].trim()) && i + 1 < parts.length) {
                    String token = parts[i + 1].trim();
                    if (!token.isBlank()) {
                        return token;
                    }
                }
            }
            for (String part : parts) {
                String trimmed = part.trim();
                String prefix = BEARER_PROTOCOL + ".";
                if (trimmed.startsWith(prefix)) {
                    String token = trimmed.substring(prefix.length()).trim();
                    if (!token.isBlank()) {
                        return token;
                    }
                }
            }
        }
        return null;
    }
}
