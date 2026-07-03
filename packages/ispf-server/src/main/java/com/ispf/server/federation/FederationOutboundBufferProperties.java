package com.ispf.server.federation;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "ispf.federation.outbound-buffer")
public class FederationOutboundBufferProperties {

    private int maxBytes = 2 * 1024 * 1024;
    private String dropPolicy = "DROP_OLDEST";

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

    public FederationOutboundEventBuffer.DropPolicy resolvedDropPolicy() {
        if ("DROP_NEWEST".equalsIgnoreCase(dropPolicy)) {
            return FederationOutboundEventBuffer.DropPolicy.DROP_NEWEST;
        }
        return FederationOutboundEventBuffer.DropPolicy.DROP_OLDEST;
    }
}
