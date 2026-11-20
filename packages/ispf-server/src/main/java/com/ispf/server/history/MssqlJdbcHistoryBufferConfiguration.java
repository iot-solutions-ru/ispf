package com.ispf.server.history;

import com.ispf.server.config.MssqlRelationalProperties;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.nio.file.Path;

@Configuration
public class MssqlJdbcHistoryBufferConfiguration {

    @Bean
    @ConditionalOnProperty(prefix = "ispf.relational.mssql", name = "jdbc-buffer-enabled", havingValue = "true")
    public MssqlJdbcHistoryBuffer mssqlJdbcHistoryBuffer(MssqlRelationalProperties properties) {
        return new MssqlJdbcHistoryBuffer(
                Path.of(properties.getJdbcBufferDirectory()),
                properties.getJdbcBufferMaxBytes()
        );
    }
}
