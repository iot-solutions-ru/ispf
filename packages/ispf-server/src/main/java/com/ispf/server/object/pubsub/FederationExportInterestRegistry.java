package com.ispf.server.object.pubsub;

import com.ispf.server.federation.FederationOutboundAgent;
import com.ispf.server.federation.FederationOutboundAgentStore;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Whether any enabled federation outbound agent exports {@code path} (ADR-0024).
 * The enabled-agent list is cached: {@code hasPathInterest} runs on every historian sample.
 */
@Component
public class FederationExportInterestRegistry {

    private static final long CACHE_TTL_MS = 1_000;

    private final FederationOutboundAgentStore agentStore;
    private volatile List<FederationOutboundAgent> enabledAgents = List.of();
    private volatile long loadedAtMs;

    public FederationExportInterestRegistry(FederationOutboundAgentStore agentStore) {
        this.agentStore = agentStore;
    }

    public boolean hasPathInterest(String path) {
        if (path == null || path.isBlank()) {
            return false;
        }
        for (FederationOutboundAgent agent : enabledAgents()) {
            if (exportsPath(agent, path)) {
                return true;
            }
        }
        return false;
    }

    private List<FederationOutboundAgent> enabledAgents() {
        long now = System.currentTimeMillis();
        List<FederationOutboundAgent> current = enabledAgents;
        if (now - loadedAtMs < CACHE_TTL_MS) {
            return current;
        }
        synchronized (this) {
            if (System.currentTimeMillis() - loadedAtMs < CACHE_TTL_MS) {
                return enabledAgents;
            }
            List<FederationOutboundAgent> loaded = List.copyOf(agentStore.listEnabled());
            enabledAgents = loaded;
            loadedAtMs = System.currentTimeMillis();
            return loaded;
        }
    }

    static boolean exportsPath(FederationOutboundAgent agent, String path) {
        String prefix = agent.pathPrefix() == null || agent.pathPrefix().isBlank()
                ? "root.platform"
                : agent.pathPrefix().trim().replaceAll("\\.+$", "");
        return path.equals(prefix) || path.startsWith(prefix + ".");
    }
}
