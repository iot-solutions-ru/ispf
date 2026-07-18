package com.ispf.server.ai.mcp;

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

    @PostMapping
    public Object message(@RequestBody Object body, Authentication authentication) throws Exception {
        String actor = actor(authentication);
        if (body instanceof List<?> list) {
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> requests = (List<Map<String, Object>>) list;
            return requests.stream()
                    .map(req -> {
                        try {
                            return jsonRpcHandler.handle(req, authentication, actor);
                        } catch (Exception ex) {
                            return Map.of(
                                    "jsonrpc", "2.0",
                                    "id", req.getOrDefault("id", 0),
                                    "error", Map.of("code", -32000, "message", ex.getMessage())
                            );
                        }
                    })
                    .toList();
        }
        if (body instanceof Map<?, ?> map) {
            @SuppressWarnings("unchecked")
            Map<String, Object> request = (Map<String, Object>) map;
            return jsonRpcHandler.handle(request, authentication, actor);
        }
        throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Expected JSON-RPC object or array");
    }

    private static String actor(Authentication authentication) {
        if (authentication == null || !authentication.isAuthenticated()) {
            return "anonymous";
        }
        return authentication.getName();
    }
}
