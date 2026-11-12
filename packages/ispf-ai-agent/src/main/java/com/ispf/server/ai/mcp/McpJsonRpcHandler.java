package com.ispf.server.ai.mcp;

import com.ispf.server.config.McpProperties;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
@ConditionalOnProperty(prefix = "ispf.mcp", name = "enabled", havingValue = "true")
public class McpJsonRpcHandler {

    private static final int SESSION_REQUIRED_CODE = -32001;

    private final McpProperties properties;
    private final McpToolAdapter toolAdapter;
    private final McpResourceAdapter resourceAdapter;
    private final McpConnectionStateStore connectionStateStore;

    public McpJsonRpcHandler(
            McpProperties properties,
            McpToolAdapter toolAdapter,
            McpResourceAdapter resourceAdapter,
            McpConnectionStateStore connectionStateStore
    ) {
        this.properties = properties;
        this.toolAdapter = toolAdapter;
        this.resourceAdapter = resourceAdapter;
        this.connectionStateStore = connectionStateStore;
    }

    public record HttpHandleResult(Map<String, Object> response, String mintedSessionId) {
    }

    @SuppressWarnings("unchecked")
    public HttpHandleResult handleHttp(
            Map<String, Object> request,
            Authentication authentication,
            String actor,
            String sessionHeader
    ) throws Exception {
        String method = request.get("method") != null ? request.get("method").toString() : "";
        Object id = request.get("id");
        if ("initialize".equals(method)) {
            String sessionId = connectionStateStore.initializeHttpSession(actor, sessionHeader);
            Map<String, Object> result = initializeResult(sessionId);
            return new HttpHandleResult(success(id, result), sessionId);
        }
        if (!"ping".equals(method)) {
            if (sessionHeader == null || sessionHeader.isBlank()) {
                return new HttpHandleResult(
                        error(
                                id,
                                SESSION_REQUIRED_CODE,
                                "Mcp-Session-Id header required — call initialize first, then send the session id "
                                        + "on every tools/call and tools/list request"
                        ),
                        null
                );
            }
            if (!connectionStateStore.hasHttpSession(actor, sessionHeader)) {
                return new HttpHandleResult(
                        error(id, SESSION_REQUIRED_CODE, "Unknown or expired Mcp-Session-Id — call initialize again"),
                        null
                );
            }
        }
        String connectionKey = sessionHeader != null && !sessionHeader.isBlank()
                ? McpConnectionKeys.forHttpActor(actor, sessionHeader.trim())
                : McpConnectionKeys.forHttpActor(actor, "noop");
        return new HttpHandleResult(handle(request, authentication, actor, connectionKey), null);
    }

    @SuppressWarnings("unchecked")
    public Map<String, Object> handle(
            Map<String, Object> request,
            Authentication authentication,
            String actor,
            String connectionKey
    ) throws Exception {
        String method = request.get("method") != null ? request.get("method").toString() : "";
        Object id = request.get("id");
        Map<String, Object> params = request.get("params") instanceof Map<?, ?> map
                ? (Map<String, Object>) map
                : Map.of();

        return switch (method) {
            case "initialize" -> success(id, initializeResult(null));
            case "tools/list" -> success(id, Map.of("tools", toolAdapter.listTools()));
            case "tools/call" -> success(id, callTool(params, authentication, actor, connectionKey));
            case "resources/list" -> success(id, Map.of("resources", resourceAdapter.listResources()));
            case "resources/read" -> success(id, readResource(params));
            case "ping" -> success(id, Map.of());
            default -> error(id, -32601, "Method not found: " + method);
        };
    }

    private Map<String, Object> initializeResult(String sessionId) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("protocolVersion", properties.getProtocolVersion());
        result.put("capabilities", Map.of(
                "tools", Map.of(),
                "resources", Map.of()
        ));
        result.put("serverInfo", Map.of(
                "name", properties.getServerName(),
                "version", "0.7.6"
        ));
        if (sessionId != null && !sessionId.isBlank()) {
            result.put("sessionId", sessionId);
        }
        return result;
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> callTool(
            Map<String, Object> params,
            Authentication authentication,
            String actor,
            String connectionKey
    ) throws Exception {
        String name = params.get("name") != null ? params.get("name").toString() : "";
        if (name.isBlank()) {
            throw new IllegalArgumentException("tools/call requires name");
        }
        Map<String, Object> arguments = params.get("arguments") instanceof Map<?, ?> map
                ? (Map<String, Object>) map
                : Map.of();
        return toolAdapter.callTool(name, arguments, authentication, actor, connectionKey);
    }

    private Map<String, Object> readResource(Map<String, Object> params) {
        String uri = params.get("uri") != null ? params.get("uri").toString() : "";
        if (uri.isBlank()) {
            throw new IllegalArgumentException("resources/read requires uri");
        }
        return resourceAdapter.readResource(uri);
    }

    private static Map<String, Object> success(Object id, Object result) {
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("jsonrpc", "2.0");
        response.put("id", id);
        response.put("result", result);
        return response;
    }

    private static Map<String, Object> error(Object id, int code, String message) {
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("jsonrpc", "2.0");
        response.put("id", id);
        response.put("error", Map.of("code", code, "message", message));
        return response;
    }

    public Map<String, Object> handleBatch(
            List<Map<String, Object>> requests,
            Authentication authentication,
            String actor,
            String connectionKey
    ) throws Exception {
        List<Map<String, Object>> responses = requests.stream()
                .map(req -> {
                    try {
                        return handle(req, authentication, actor, connectionKey);
                    } catch (Exception ex) {
                        Object id = req.get("id");
                        return Map.of(
                                "jsonrpc", "2.0",
                                "id", id != null ? id : 0,
                                "error", Map.of("code", -32000, "message", ex.getMessage())
                        );
                    }
                })
                .toList();
        return Map.of("responses", responses);
    }
}
