package com.ispf.server.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "ispf.ui-pack")
public class UiPackProperties {

    /** Directory for hosted UI packs (`ui-pack.json` + static assets). */
    private String packsDir = "./data/ui-packs";

    /** Max zip size accepted on install (bytes). Default 50 MiB. */
    private long maxZipBytes = 50L * 1024L * 1024L;

    public String getPacksDir() {
        return packsDir;
    }

    public void setPacksDir(String packsDir) {
        this.packsDir = packsDir;
    }

    public long getMaxZipBytes() {
        return maxZipBytes;
    }

    public void setMaxZipBytes(long maxZipBytes) {
        this.maxZipBytes = maxZipBytes;
    }
}
