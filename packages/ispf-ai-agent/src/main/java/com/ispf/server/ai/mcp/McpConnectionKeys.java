package com.ispf.server.ai.mcp;

/**
 * Stable connection keys for {@link McpConnectionStateStore}.
 */
public final class McpConnectionKeys {

    private McpConnectionKeys() {
    }

  /** HTTP MCP: one run-state bucket per authenticated principal name. */
    public static String forHttpActor(String actor) {
        String name = actor != null && !actor.isBlank() ? actor.trim() : "anonymous";
        return "http:" + name;
    }

    /** Stdio MCP: one run-state bucket per OS process connection. */
    public static String forStdioConnection(String connectionId) {
        return "stdio:" + connectionId;
    }
}
