package com.ispf.server.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "ispf.security")
public class IspfSecurityProperties {

    /**
     * When false, all API endpoints are open (used in integration tests).
     */
    private boolean rbacEnabled = true;

    /**
     * Default role for local profile when X-ISPF-Role header is absent.
     */
    private String localDefaultRole = "admin";

    public boolean isRbacEnabled() {
        return rbacEnabled;
    }

    public void setRbacEnabled(boolean rbacEnabled) {
        this.rbacEnabled = rbacEnabled;
    }

    public String getLocalDefaultRole() {
        return localDefaultRole;
    }

    public void setLocalDefaultRole(String localDefaultRole) {
        this.localDefaultRole = localDefaultRole;
    }
}
