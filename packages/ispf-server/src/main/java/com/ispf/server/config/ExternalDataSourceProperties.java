package com.ispf.server.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.List;

@ConfigurationProperties(prefix = "ispf.datasource.external")
public class ExternalDataSourceProperties {

    /**
     * Allowed JDBC driver class name prefixes (security allowlist).
     */
    private List<String> allowedDriverPrefixes = List.of(
            "org.postgresql.",
            "com.microsoft.sqlserver.",
            "com.mysql.",
            "org.mariadb.",
            "oracle.jdbc.",
            "com.oracle.",
            "org.h2."
    );

    private int defaultPoolSize = 5;
    private int maxPoolSize = 20;

    public List<String> getAllowedDriverPrefixes() {
        return allowedDriverPrefixes;
    }

    public void setAllowedDriverPrefixes(List<String> allowedDriverPrefixes) {
        this.allowedDriverPrefixes = allowedDriverPrefixes;
    }

    public int getDefaultPoolSize() {
        return defaultPoolSize;
    }

    public void setDefaultPoolSize(int defaultPoolSize) {
        this.defaultPoolSize = defaultPoolSize;
    }

    public int getMaxPoolSize() {
        return maxPoolSize;
    }

    public void setMaxPoolSize(int maxPoolSize) {
        this.maxPoolSize = maxPoolSize;
    }
}
