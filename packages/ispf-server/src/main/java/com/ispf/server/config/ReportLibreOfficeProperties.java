package com.ispf.server.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "ispf.reports.libre-office")
public class ReportLibreOfficeProperties {

    /** Path to Libre/OpenOffice program dir (contains soffice). Empty = auto-detect. */
    private String path = "";
    private int timeoutSeconds = 120;
    private boolean displayDeviceAvailable = false;

    public String getPath() {
        return path;
    }

    public void setPath(String path) {
        this.path = path;
    }

    public int getTimeoutSeconds() {
        return timeoutSeconds;
    }

    public void setTimeoutSeconds(int timeoutSeconds) {
        this.timeoutSeconds = timeoutSeconds;
    }

    public boolean isDisplayDeviceAvailable() {
        return displayDeviceAvailable;
    }

    public void setDisplayDeviceAvailable(boolean displayDeviceAvailable) {
        this.displayDeviceAvailable = displayDeviceAvailable;
    }
}
