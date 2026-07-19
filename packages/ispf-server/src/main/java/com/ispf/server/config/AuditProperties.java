package com.ispf.server.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Security audit trail settings — BL-156.
 */
@ConfigurationProperties(prefix = "ispf.audit")
public class AuditProperties {

    /**
     * When set, each audit event is POSTed as JSON to this URL (SIEM / webhook collector).
     * Empty disables the webhook.
     */
    private String siemWebhookUrl = "";

    /** HTTP timeout for SIEM webhook posts. */
    private int siemTimeoutSeconds = 5;

    /**
     * When true, SIEM dispatch failures are logged but do not roll back the audit row.
     * Always true for GA — audit persist must not depend on SIEM availability.
     */
    private boolean siemAsync = true;

    public String getSiemWebhookUrl() {
        return siemWebhookUrl;
    }

    public void setSiemWebhookUrl(String siemWebhookUrl) {
        this.siemWebhookUrl = siemWebhookUrl != null ? siemWebhookUrl.trim() : "";
    }

    public boolean isSiemWebhookEnabled() {
        return siemWebhookUrl != null && !siemWebhookUrl.isBlank();
    }

    public int getSiemTimeoutSeconds() {
        return siemTimeoutSeconds;
    }

    public void setSiemTimeoutSeconds(int siemTimeoutSeconds) {
        this.siemTimeoutSeconds = Math.max(1, siemTimeoutSeconds);
    }

    public boolean isSiemAsync() {
        return siemAsync;
    }

    public void setSiemAsync(boolean siemAsync) {
        this.siemAsync = siemAsync;
    }
}
