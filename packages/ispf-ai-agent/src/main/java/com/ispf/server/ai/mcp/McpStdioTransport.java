package com.ispf.server.ai.mcp;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

/**
 * JSON-RPC over stdin/stdout for local MCP clients (Cursor, CI). Active when {@code ispf.mcp.stdio-enabled=true}.
 */
@Component
@ConditionalOnProperty(prefix = "ispf.mcp", name = {"enabled", "stdio-enabled"}, havingValue = "true")
public class McpStdioTransport implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(McpStdioTransport.class);

    private final McpJsonRpcHandler jsonRpcHandler;
    private final ObjectMapper objectMapper;

    public McpStdioTransport(McpJsonRpcHandler jsonRpcHandler, ObjectMapper objectMapper) {
        this.jsonRpcHandler = jsonRpcHandler;
        this.objectMapper = objectMapper;
    }

    @Override
    public void run(ApplicationArguments args) throws Exception {
        log.info("MCP stdio transport started (one JSON-RPC object per line on stdin)");
        UsernamePasswordAuthenticationToken authentication =
                new UsernamePasswordAuthenticationToken("mcp-stdio", null, List.of());
        String actor = "mcp-stdio";

        try (BufferedReader reader = new BufferedReader(new InputStreamReader(System.in, StandardCharsets.UTF_8));
             PrintWriter writer = new PrintWriter(System.out, true, StandardCharsets.UTF_8)) {
            String line;
            while ((line = reader.readLine()) != null) {
                if (line.isBlank()) {
                    continue;
                }
                try {
                    Object parsed = objectMapper.readValue(line, Object.class);
                    if (parsed instanceof List<?> list) {
                        @SuppressWarnings("unchecked")
                        List<Map<String, Object>> batch = (List<Map<String, Object>>) list;
                        for (Map<String, Object> request : batch) {
                            writer.println(objectMapper.writeValueAsString(
                                    jsonRpcHandler.handle(request, authentication, actor)));
                        }
                    } else if (parsed instanceof Map<?, ?> map) {
                        @SuppressWarnings("unchecked")
                        Map<String, Object> request = (Map<String, Object>) map;
                        writer.println(objectMapper.writeValueAsString(
                                jsonRpcHandler.handle(request, authentication, actor)));
                    }
                } catch (Exception ex) {
                    log.debug("MCP stdio parse error: {}", ex.getMessage());
                    writer.println(objectMapper.writeValueAsString(Map.of(
                            "jsonrpc", "2.0",
                            "id", 0,
                            "error", Map.of("code", -32700, "message", ex.getMessage())
                    )));
                }
            }
        }
    }
}
