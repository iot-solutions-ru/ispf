package com.ispf.server.platform;

import java.util.Map;

/**
 * Seam for AI agent metrics contributed by {@code ispf-ai-agent} without a compile cycle.
 */
public interface AgentMetricsPort {

    Map<String, Object> agentSnapshot();
}
