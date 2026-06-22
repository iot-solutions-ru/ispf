package com.ispf.server.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "ispf.mcp")
public class McpProperties {

    /**
     * When true, exposes MCP JSON-RPC over HTTP at {@code /api/v1/ai/mcp}.
     */
    private boolean enabled = false;

    /**
     * MCP server name reported in {@code initialize} response.
     */
    private String serverName = "ispf-platform";

    /**
     * MCP protocol version (client compatibility hint).
     */
    private String protocolVersion = "2024-11-05";

    /**
     * When true with profile {@code mcp}, reads JSON-RPC from stdin and writes responses to stdout.
     */
    private boolean stdioEnabled = false;

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public String getServerName() {
        return serverName;
    }

    public void setServerName(String serverName) {
        this.serverName = serverName;
    }

    public String getProtocolVersion() {
        return protocolVersion;
    }

    public void setProtocolVersion(String protocolVersion) {
        this.protocolVersion = protocolVersion;
    }

    public boolean isStdioEnabled() {
        return stdioEnabled;
    }

    public void setStdioEnabled(boolean stdioEnabled) {
        this.stdioEnabled = stdioEnabled;
    }
}
