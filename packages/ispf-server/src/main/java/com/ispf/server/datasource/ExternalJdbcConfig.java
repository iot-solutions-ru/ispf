package com.ispf.server.datasource;

/**
 * JDBC connection settings for external platform data sources (ADR-0037).
 */
public record ExternalJdbcConfig(
        String jdbcUrl,
        String driverClassName,
        String username,
        String password,
        int maximumPoolSize
) {
}
