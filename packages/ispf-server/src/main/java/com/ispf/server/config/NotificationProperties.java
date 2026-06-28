package com.ispf.server.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "ispf.notifications")
public class NotificationProperties {

    /** Optional HTTP relay for SEND_EMAIL (JSON: to, subject, body). */
    private String emailRelayUrl = "";

    private int timeoutSeconds = 15;

    public String getEmailRelayUrl() {
        return emailRelayUrl;
    }

    public void setEmailRelayUrl(String emailRelayUrl) {
        this.emailRelayUrl = emailRelayUrl;
    }

    public int getTimeoutSeconds() {
        return timeoutSeconds;
    }

    public void setTimeoutSeconds(int timeoutSeconds) {
        this.timeoutSeconds = timeoutSeconds;
    }
}
