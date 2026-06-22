package com.ispf.server.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "ispf.driver")
public class DriverPackProperties {

    /** Directory scanned for licensed driver packs (`driver-pack.json` + JAR). */
    private String packsDir = "./data/drivers";

    public String getPacksDir() {
        return packsDir;
    }

    public void setPacksDir(String packsDir) {
        this.packsDir = packsDir;
    }
}
