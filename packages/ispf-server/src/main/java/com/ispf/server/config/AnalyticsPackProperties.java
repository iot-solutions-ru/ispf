package com.ispf.server.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "ispf.analytics-pack")
public class AnalyticsPackProperties {

    /** Directory scanned for drop-in analytics extension packs (`analytics-pack.json` + JAR). */
    private String packsDir = "./data/analytics-packs";

    public String getPacksDir() {
        return packsDir;
    }

    public void setPacksDir(String packsDir) {
        this.packsDir = packsDir;
    }
}
