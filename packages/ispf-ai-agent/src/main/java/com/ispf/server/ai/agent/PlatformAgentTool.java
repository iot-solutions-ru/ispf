package com.ispf.server.ai.agent;

import java.util.Map;

public interface PlatformAgentTool {

    String name();

    String description();

    /**
     * JSON Schema for tool arguments (ADR-0051). Default: catalog entry for {@link #name()}.
     */
    default Map<String, Object> inputSchema() {
        return AgentToolInputSchemas.forTool(name());
    }

    Map<String, Object> execute(Map<String, Object> arguments, AgentContext context) throws Exception;
}
