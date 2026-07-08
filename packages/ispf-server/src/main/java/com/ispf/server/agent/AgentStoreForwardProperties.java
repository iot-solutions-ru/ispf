package com.ispf.server.agent;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "ispf.agent.store-forward")
public class AgentStoreForwardProperties {

    private boolean enabled = true;
    private boolean persistToDisk = true;
    private int maxBytes = 2 * 1024 * 1024;
    private String dropPolicy = "DROP_OLDEST";

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public boolean isPersistToDisk() {
        return persistToDisk;
    }

    public void setPersistToDisk(boolean persistToDisk) {
        this.persistToDisk = persistToDisk;
    }

    public int maxBytes() {
        return maxBytes;
    }

    public void setMaxBytes(int maxBytes) {
        this.maxBytes = maxBytes;
    }

    public String dropPolicy() {
        return dropPolicy;
    }

    public void setDropPolicy(String dropPolicy) {
        this.dropPolicy = dropPolicy;
    }
}
