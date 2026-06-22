package com.ispf.server.ai.agent;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Per agent-run mutable state (e.g. bundle validation gates before import).
 */
public final class AgentRunState {

    private final Map<String, Boolean> validatedBundles = new ConcurrentHashMap<>();

    public void markBundleValidated(String appId) {
        if (appId != null && !appId.isBlank()) {
            validatedBundles.put(appId.trim(), true);
        }
    }

    public boolean isBundleValidated(String appId) {
        return appId != null && Boolean.TRUE.equals(validatedBundles.get(appId.trim()));
    }
}
