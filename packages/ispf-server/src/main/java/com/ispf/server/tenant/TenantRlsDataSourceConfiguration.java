package com.ispf.server.tenant;

import com.ispf.server.config.TenantIsolationProperties;
import com.zaxxer.hikari.HikariDataSource;
import org.springframework.boot.jdbc.autoconfigure.DataSourceProperties;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

import javax.sql.DataSource;

/**
 * Wraps the primary DataSource with {@link TenantRlsHikariDataSource} when
 * PostgreSQL + {@code ispf.tenant.db-row-isolation=true}.
 */
@Configuration
@EnableConfigurationProperties(DataSourceProperties.class)
public class TenantRlsDataSourceConfiguration {

    @Bean
    @Primary
    @ConfigurationProperties(prefix = "spring.datasource.hikari")
    DataSource dataSource(
            DataSourceProperties properties,
            TenantIsolationProperties tenantIsolationProperties
    ) {
        Class<? extends DataSource> type =
                shouldApplyRls(properties, tenantIsolationProperties)
                        ? TenantRlsHikariDataSource.class
                        : HikariDataSource.class;
        return properties.initializeDataSourceBuilder().type(type).build();
    }

    static boolean shouldApplyRls(
            DataSourceProperties properties,
            TenantIsolationProperties tenantIsolationProperties
    ) {
        if (tenantIsolationProperties == null || !tenantIsolationProperties.isDbRowIsolation()) {
            return false;
        }
        String url = properties.getUrl();
        if (url != null) {
            String lower = url.toLowerCase();
            // Require jdbc:postgresql — H2 URLs often contain MODE=PostgreSQL.
            if (lower.startsWith("jdbc:postgresql:") || lower.startsWith("jdbc:pgsql:")) {
                return true;
            }
        }
        String driver = properties.getDriverClassName();
        return driver != null && driver.toLowerCase().contains("org.postgresql.");
    }
}
