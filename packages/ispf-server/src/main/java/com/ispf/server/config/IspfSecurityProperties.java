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
     * Empty = require Bearer token login.
     */
    private String localDefaultRole = "";

    /**
     * When true, login token is required unless X-ISPF-Role header is sent (dev fallback).
     */
    private boolean tokenAuthEnabled = true;

    /**
     * OIDC client id for Web Console (Authorization Code + PKCE).
     */
    private String oidcClientId = "ispf-web-console";

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

    public boolean isTokenAuthEnabled() {
        return tokenAuthEnabled;
    }

    public void setTokenAuthEnabled(boolean tokenAuthEnabled) {
        this.tokenAuthEnabled = tokenAuthEnabled;
    }

    public String getOidcClientId() {
        return oidcClientId;
    }

    public void setOidcClientId(String oidcClientId) {
        this.oidcClientId = oidcClientId;
    }
}
