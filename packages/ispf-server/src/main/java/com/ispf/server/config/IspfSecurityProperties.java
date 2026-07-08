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

    /**
     * Master key for encrypting federation service-account passwords (AES-GCM).
     * When blank, only static Bearer tokens can be stored on peers.
     */
    private String secretsKey = "";

    private final Mfa mfa = new Mfa();

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

    public String getSecretsKey() {
        return secretsKey;
    }

    public void setSecretsKey(String secretsKey) {
        this.secretsKey = secretsKey;
    }

    public Mfa getMfa() {
        return mfa;
    }

    public static class Mfa {
        /**
         * When true, TOTP enrollment endpoints are active ({@code /api/v1/security/mfa/**}).
         */
        private boolean enabled = false;

        /**
         * When true (and {@link #enabled}), admin-role logins require enrolled MFA and a valid TOTP code.
         */
        private boolean requiredForAdmin = false;

        /**
         * Allowed TOTP drift in 30-second steps (±N). Default {@code 1} accepts previous/current/next window.
         */
        private int timeWindowSteps = 1;

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public boolean isRequiredForAdmin() {
            return requiredForAdmin;
        }

        public void setRequiredForAdmin(boolean requiredForAdmin) {
            this.requiredForAdmin = requiredForAdmin;
        }

        public int getTimeWindowSteps() {
            return timeWindowSteps;
        }

        public void setTimeWindowSteps(int timeWindowSteps) {
            this.timeWindowSteps = timeWindowSteps;
        }
    }
}
