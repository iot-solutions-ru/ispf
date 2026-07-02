package com.ispf.server.object.pubsub;

import com.ispf.server.federation.FederationOutboundAgent;
import com.ispf.server.federation.FederationOutboundAgentStore;
import org.springframework.stereotype.Component;

/**
 * Whether any enabled federation outbound agent exports {@code path} (ADR-0024).
 */
@Component
public class FederationExportInterestRegistry {

    private final FederationOutboundAgentStore agentStore;

    public FederationExportInterestRegistry(FederationOutboundAgentStore agentStore) {
        this.agentStore = agentStore;
    }

    public boolean hasPathInterest(String path) {
        if (path == null || path.isBlank()) {
            return false;
        }
        for (FederationOutboundAgent agent : agentStore.listEnabled()) {
            if (exportsPath(agent, path)) {
                return true;
            }
        }
        return false;
    }

    static boolean exportsPath(FederationOutboundAgent agent, String path) {
        String prefix = agent.pathPrefix() == null || agent.pathPrefix().isBlank()
                ? "root.platform"
                : agent.pathPrefix().trim().replaceAll("\\.+$", "");
        return path.equals(prefix) || path.startsWith(prefix + ".");
    }
}
