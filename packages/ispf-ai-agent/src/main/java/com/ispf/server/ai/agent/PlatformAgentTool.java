package com.ispf.server.ai.agent;

import java.util.Map;

public interface PlatformAgentTool {

    String name();

    String description();

    Map<String, Object> execute(Map<String, Object> arguments, AgentContext context) throws Exception;
}
