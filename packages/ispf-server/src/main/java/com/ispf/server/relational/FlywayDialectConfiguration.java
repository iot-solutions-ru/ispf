package com.ispf.server.relational;

import org.springframework.boot.flyway.autoconfigure.FlywayConfigurationCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import javax.sql.DataSource;

@Configuration
public class FlywayDialectConfiguration {

    @Bean
    FlywayConfigurationCustomizer flywayDialectCustomizer(
            DataSource dataSource,
            RelationalDialectDetector relationalDialectDetector
    ) {
        return configuration -> configuration.locations(
                relationalDialectDetector.resolve(dataSource).flywayLocations()
        );
    }
}
