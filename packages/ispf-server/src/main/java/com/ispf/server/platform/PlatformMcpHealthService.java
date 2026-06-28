package com.ispf.server.platform;

import com.ispf.server.ai.agent.PlatformAgentToolRegistry;
import com.ispf.server.config.McpProperties;
import org.springframework.stereotype.Service;

@Service
public class PlatformMcpHealthService {

    private final McpProperties mcpProperties;
    private final PlatformAgentToolRegistry toolRegistry;

    public PlatformMcpHealthService(McpProperties mcpProperties, PlatformAgentToolRegistry toolRegistry) {
        this.mcpProperties = mcpProperties;
        this.toolRegistry = toolRegistry;
    }

    public McpHealth health() {
        boolean enabled = mcpProperties.isEnabled();
        return new McpHealth(
                enabled,
                mcpProperties.isStdioEnabled(),
                mcpProperties.getServerName(),
                mcpProperties.getProtocolVersion(),
                enabled ? toolRegistry.toolCatalog().size() : 0,
                enabled ? "/api/v1/ai/mcp" : null
        );
    }

    public record McpHealth(
            boolean enabled,
            boolean stdioEnabled,
            String serverName,
            String protocolVersion,
            int toolCount,
            String httpEndpoint
    ) {
    }
}
