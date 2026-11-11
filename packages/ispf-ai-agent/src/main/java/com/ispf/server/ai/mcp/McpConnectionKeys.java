package com.ispf.server.ai.mcp;

/**
 * Stable connection keys for {@link McpConnectionStateStore}.
 */
public final class McpConnectionKeys {

    private McpConnectionKeys() {
    }

    /**
     * HTTP MCP: run-state bucket per authenticated principal.
     * Optional {@code connectionId} (e.g. {@code X-ISPF-Mcp-Connection} header) isolates concurrent
     * HTTP MCP clients that share the same principal; omit it for single-client actor affinity.
     */
    public static String forHttpActor(String actor) {
        return forHttpActor(actor, null);
    }

    public static String forHttpActor(String actor, String connectionId) {
        String name = actor != null && !actor.isBlank() ? actor.trim() : "anonymous";
        if (connectionId == null || connectionId.isBlank()) {
            return "http:" + name;
        }
        return "http:" + name + ":" + connectionId.trim();
    }

    /** Stdio MCP: one run-state bucket per OS process connection. */
    public static String forStdioConnection(String connectionId) {
        return "stdio:" + connectionId;
    }
}
