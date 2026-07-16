package com.ispf.server.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "ispf.federation")
public class FederationSecurityProperties {

    /**
     * Comma-separated host allowlist for outbound federation HTTP (login/probe).
     * Empty = any non-blocked host. Supports {@code *.example.com} suffix patterns.
     */
    private String outboundUrlAllowlist = "";

    /**
     * When true, reject loopback / unspecified addresses for outbound federation URLs.
     * Keep false for local/lab peer dogfood on 127.0.0.1.
     */
    private boolean blockLoopbackHosts = false;

    public String getOutboundUrlAllowlist() {
        return outboundUrlAllowlist;
    }

    public void setOutboundUrlAllowlist(String outboundUrlAllowlist) {
        this.outboundUrlAllowlist = outboundUrlAllowlist == null ? "" : outboundUrlAllowlist;
    }

    public boolean isBlockLoopbackHosts() {
        return blockLoopbackHosts;
    }

    public void setBlockLoopbackHosts(boolean blockLoopbackHosts) {
        this.blockLoopbackHosts = blockLoopbackHosts;
    }
}
