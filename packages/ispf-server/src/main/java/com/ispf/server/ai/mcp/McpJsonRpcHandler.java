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

    private final McpProperties properties;
    private final McpToolAdapter toolAdapter;
    private final McpResourceAdapter resourceAdapter;

    public McpJsonRpcHandler(
            McpProperties properties,
            McpToolAdapter toolAdapter,
            McpResourceAdapter resourceAdapter
    ) {
        this.properties = properties;
        this.toolAdapter = toolAdapter;
        this.resourceAdapter = resourceAdapter;
    }

    @SuppressWarnings("unchecked")
    public Map<String, Object> handle(Map<String, Object> request, Authentication authentication, String actor)
            throws Exception {
        String method = request.get("method") != null ? request.get("method").toString() : "";
        Object id = request.get("id");
        Map<String, Object> params = request.get("params") instanceof Map<?, ?> map
                ? (Map<String, Object>) map
                : Map.of();

        return switch (method) {
            case "initialize" -> success(id, initializeResult());
            case "tools/list" -> success(id, Map.of("tools", toolAdapter.listTools()));
            case "tools/call" -> success(id, callTool(params, authentication, actor));
            case "resources/list" -> success(id, Map.of("resources", resourceAdapter.listResources()));
            case "resources/read" -> success(id, readResource(params));
            case "ping" -> success(id, Map.of());
            default -> error(id, -32601, "Method not found: " + method);
        };
    }

    private Map<String, Object> initializeResult() {
        return Map.of(
                "protocolVersion", properties.getProtocolVersion(),
                "capabilities", Map.of(
                        "tools", Map.of(),
                        "resources", Map.of()
                ),
                "serverInfo", Map.of(
                        "name", properties.getServerName(),
                        "version", "0.7.6"
                )
        );
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> callTool(
            Map<String, Object> params,
            Authentication authentication,
            String actor
    ) throws Exception {
        String name = params.get("name") != null ? params.get("name").toString() : "";
        if (name.isBlank()) {
            throw new IllegalArgumentException("tools/call requires name");
        }
        Map<String, Object> arguments = params.get("arguments") instanceof Map<?, ?> map
                ? (Map<String, Object>) map
                : Map.of();
        return toolAdapter.callTool(name, arguments, authentication, actor);
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

    public Map<String, Object> handleBatch(List<Map<String, Object>> requests, Authentication authentication, String actor)
            throws Exception {
        List<Map<String, Object>> responses = requests.stream()
                .map(req -> {
                    try {
                        return handle(req, authentication, actor);
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
