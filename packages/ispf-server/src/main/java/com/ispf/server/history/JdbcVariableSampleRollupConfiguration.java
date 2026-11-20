package com.ispf.server.history;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;

@Configuration
@ConditionalOnProperty(name = "ispf.variable-history.store", havingValue = "jdbc", matchIfMissing = true)
public class JdbcVariableSampleRollupConfiguration {

    @Bean
    @ConditionalOnProperty(prefix = "ispf.relational.mssql", name = "single-engine", havingValue = "true")
    public JdbcVariableSampleRollupStore jdbcVariableSampleRollupStore(JdbcTemplate jdbcTemplate) {
        return new JdbcVariableSampleRollupStore(jdbcTemplate);
    }
}
