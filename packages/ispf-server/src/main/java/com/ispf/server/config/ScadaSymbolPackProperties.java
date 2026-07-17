package com.ispf.server.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "ispf.scada")
public class ScadaSymbolPackProperties {

    /** Directory scanned for drop-in SCADA symbol packs (`manifest.json` + category JSON). */
    private String symbolPacksDir = "./data/symbol-packs";

    public String getSymbolPacksDir() {
        return symbolPacksDir;
    }

    public void setSymbolPacksDir(String symbolPacksDir) {
        this.symbolPacksDir = symbolPacksDir;
    }
}
