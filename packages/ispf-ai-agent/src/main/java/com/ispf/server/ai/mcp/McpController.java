package com.ispf.server.ai.mcp;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Map;

/**
 * HTTP transport for MCP JSON-RPC (admin-only, same auth as {@code /api/v1/ai/agent/**}).
 */
@RestController
@RequestMapping("/api/v1/ai/mcp")
@ConditionalOnProperty(prefix = "ispf.mcp", name = "enabled", havingValue = "true")
public class McpController {

    private final McpJsonRpcHandler jsonRpcHandler;

    public McpController(McpJsonRpcHandler jsonRpcHandler) {
        this.jsonRpcHandler = jsonRpcHandler;
    }

    /** Standard MCP session header (preferred). */
    public static final String SESSION_HEADER = "Mcp-Session-Id";

    /** Legacy alias used by early ISPF MCP clients. */
    public static final String CONNECTION_HEADER = "X-ISPF-Mcp-Connection";

    @PostMapping
    public Object message(
            @RequestBody Object body,
            Authentication authentication,
            HttpServletRequest httpRequest,
            HttpServletResponse httpResponse
    ) throws Exception {
        String actor = actor(authentication);
        String sessionHeader = sessionHeader(httpRequest);
        if (body instanceof List<?> list) {
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> requests = (List<Map<String, Object>>) list;
            return requests.stream()
                    .map(req -> handleOne(req, authentication, actor, sessionHeader, httpResponse))
                    .toList();
        }
        if (body instanceof Map<?, ?> map) {
            @SuppressWarnings("unchecked")
            Map<String, Object> request = (Map<String, Object>) map;
            return handleOne(request, authentication, actor, sessionHeader, httpResponse);
        }
        throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Expected JSON-RPC object or array");
    }

    private Map<String, Object> handleOne(
            Map<String, Object> request,
            Authentication authentication,
            String actor,
            String sessionHeader,
            HttpServletResponse httpResponse
    ) {
        try {
            McpJsonRpcHandler.HttpHandleResult handled = jsonRpcHandler.handleHttp(
                    request,
                    authentication,
                    actor,
                    sessionHeader
            );
            if (handled.mintedSessionId() != null) {
                httpResponse.setHeader(SESSION_HEADER, handled.mintedSessionId());
            }
            return handled.response();
        } catch (Exception ex) {
            return Map.of(
                    "jsonrpc", "2.0",
                    "id", request.getOrDefault("id", 0),
                    "error", Map.of("code", -32000, "message", ex.getMessage())
            );
        }
    }

    static String sessionHeader(HttpServletRequest httpRequest) {
        String value = httpRequest.getHeader(SESSION_HEADER);
        if (value == null || value.isBlank()) {
            value = httpRequest.getHeader(CONNECTION_HEADER);
        }
        return value != null && !value.isBlank() ? value.trim() : null;
    }

    private static String actor(Authentication authentication) {
        if (authentication == null || !authentication.isAuthenticated()) {
            return "anonymous";
        }
        return authentication.getName();
    }
}
