package com.ispf.server.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.ArrayList;
import java.util.List;

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
     * When true, {@code X-ISPF-Role} may authenticate requests without Bearer token (local/test only).
     * Disabled by default — must not be enabled on internet-facing deployments.
     */
    private boolean localRoleHeaderEnabled = false;

    /**
     * OIDC client id for Web Console (Authorization Code + PKCE).
     */
    private String oidcClientId = "ispf-web-console";

    /**
     * Master key for encrypting federation service-account passwords (AES-GCM).
     * When blank, only static Bearer tokens can be stored on peers.
     */
    private String secretsKey = "";

    /**
     * Reverse-proxy IPs whose {@code X-Forwarded-For} header is trusted when resolving the
     * client IP for login rate limiting. Empty (default) = never trust XFF; the direct peer
     * address is used, so clients cannot rotate XFF to bypass {@link Login} lockouts.
     * Leave empty when {@code server.forward-headers-strategy} is configured: the container
     * then already rewrites {@code remoteAddr} from forwarded headers before this is consulted
     * (the stock application*.yml does not set that strategy).
     */
    private List<String> trustedProxyIps = new ArrayList<>();

    private final Mfa mfa = new Mfa();
    private final Login login = new Login();

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

    public boolean isLocalRoleHeaderEnabled() {
        return localRoleHeaderEnabled;
    }

    public void setLocalRoleHeaderEnabled(boolean localRoleHeaderEnabled) {
        this.localRoleHeaderEnabled = localRoleHeaderEnabled;
    }

    public Login getLogin() {
        return login;
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

    public List<String> getTrustedProxyIps() {
        return trustedProxyIps;
    }

    public void setTrustedProxyIps(List<String> trustedProxyIps) {
        this.trustedProxyIps = trustedProxyIps;
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

    public static class Login {
        /** Failed attempts per username+IP before temporary lockout. */
        private int maxFailedAttempts = 5;

        /** Lockout window after exceeding {@link #maxFailedAttempts}. */
        private int lockoutMinutes = 15;

        public int getMaxFailedAttempts() {
            return maxFailedAttempts;
        }

        public void setMaxFailedAttempts(int maxFailedAttempts) {
            this.maxFailedAttempts = maxFailedAttempts;
        }

        public int getLockoutMinutes() {
            return lockoutMinutes;
        }

        public void setLockoutMinutes(int lockoutMinutes) {
            this.lockoutMinutes = lockoutMinutes;
        }
    }
}
