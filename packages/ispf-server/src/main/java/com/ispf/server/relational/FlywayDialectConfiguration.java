package com.ispf.server.relational;

import org.springframework.boot.flyway.autoconfigure.FlywayConfigurationCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import javax.sql.DataSource;
import java.util.HashMap;
import java.util.Map;

@Configuration
public class FlywayDialectConfiguration {

    @Bean
    FlywayConfigurationCustomizer flywayDialectCustomizer(
            DataSource dataSource,
            RelationalDialectDetector relationalDialectDetector
    ) {
        return configuration -> {
            RelationalDialect dialect = relationalDialectDetector.resolve(dataSource);
            configuration.locations(dialect.flywayLocations());
            // PostgreSQL-only bodies (V86 RLS, V89 random_uuid, …): H2 also loads the
            // postgresql pack, so wrap those sections in placeholders → block comments on H2.
            Map<String, String> placeholders = new HashMap<>();
            if (dialect.kind() == RelationalDbKind.H2) {
                placeholders.put("rls_block_start", "/*");
                placeholders.put("rls_block_end", "*/");
            } else {
                placeholders.put("rls_block_start", "");
                placeholders.put("rls_block_end", "");
            }
            configuration.placeholders(placeholders);
        };
    }
}
