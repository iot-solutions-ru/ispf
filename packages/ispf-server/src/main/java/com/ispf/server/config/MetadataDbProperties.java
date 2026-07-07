package com.ispf.server.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "ispf.metadata.db")
public class MetadataDbProperties {

    /**
     * Optional override: postgresql, h2, mssql, mysql, oracle. Empty = detect from JDBC URL.
     */
    private String kind = "";

    public String getKind() {
        return kind;
    }

    public void setKind(String kind) {
        this.kind = kind;
    }
}
