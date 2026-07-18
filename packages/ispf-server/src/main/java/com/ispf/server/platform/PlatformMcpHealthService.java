package com.ispf.server.platform;

import com.ispf.server.config.McpProperties;
import org.springframework.stereotype.Service;

import java.util.Optional;

@Service
public class PlatformMcpHealthService {

    private final McpProperties mcpProperties;
    private final Optional<McpToolCatalogPort> toolCatalogPort;

    public PlatformMcpHealthService(McpProperties mcpProperties, Optional<McpToolCatalogPort> toolCatalogPort) {
        this.mcpProperties = mcpProperties;
        this.toolCatalogPort = toolCatalogPort != null ? toolCatalogPort : Optional.empty();
    }

    public McpHealth health() {
        boolean enabled = mcpProperties.isEnabled();
        int toolCount = enabled ? toolCatalogPort.map(McpToolCatalogPort::toolCount).orElse(0) : 0;
        return new McpHealth(
                enabled,
                mcpProperties.isStdioEnabled(),
                mcpProperties.getServerName(),
                mcpProperties.getProtocolVersion(),
                toolCount,
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
