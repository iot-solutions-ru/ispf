package com.ispf.server.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Alarm shelving approval workflow — BL-158.
 */
@ConfigurationProperties(prefix = "ispf.alarm-shelf")
public class AlarmShelfProperties {

    /** When true, {@code POST /alarm-shelves} creates a pending approval instead of shelving immediately. */
    private boolean approvalRequired = false;

    public boolean isApprovalRequired() {
        return approvalRequired;
    }

    public void setApprovalRequired(boolean approvalRequired) {
        this.approvalRequired = approvalRequired;
    }
}
