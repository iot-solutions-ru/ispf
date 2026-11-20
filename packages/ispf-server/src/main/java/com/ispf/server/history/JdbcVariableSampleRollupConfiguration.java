package com.ispf.server.history;

import com.ispf.server.config.ClusterProperties;
import com.ispf.server.config.MssqlRelationalProperties;
import com.ispf.server.config.VariableHistoryProperties;
import com.ispf.server.platform.PlatformLeaderLockService;
import com.ispf.server.relational.RelationalDialect;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;

@Configuration
@ConditionalOnProperty(name = "ispf.variable-history.store", havingValue = "jdbc", matchIfMissing = true)
public class JdbcVariableSampleRollupConfiguration {

    @Bean
    @ConditionalOnProperty(prefix = "ispf.relational.mssql", name = "single-engine", havingValue = "true")
    public JdbcVariableSampleRollupStore jdbcVariableSampleRollupStore(JdbcTemplate jdbcTemplate) {
        return new JdbcVariableSampleRollupStore(jdbcTemplate);
    }

    @Bean
    @ConditionalOnProperty(prefix = "ispf.relational.mssql", name = "single-engine", havingValue = "true")
    public JdbcMssqlHistorianRollupMaterializer jdbcMssqlHistorianRollupMaterializer(
            JdbcTemplate jdbcTemplate,
            MssqlRelationalProperties mssqlRelationalProperties,
            VariableHistoryProperties variableHistoryProperties,
            ClusterProperties clusterProperties,
            RelationalDialect relationalDialect,
            PlatformTransactionManager transactionManager
    ) {
        return new JdbcMssqlHistorianRollupMaterializer(
                jdbcTemplate,
                mssqlRelationalProperties,
                variableHistoryProperties,
                clusterProperties,
                relationalDialect,
                transactionManager
        );
    }

    @Bean
    @ConditionalOnProperty(prefix = "ispf.relational.mssql", name = "single-engine", havingValue = "true")
    public JdbcMssqlHistorianRollupMaterializerRunner jdbcMssqlHistorianRollupMaterializerRunner(
            JdbcMssqlHistorianRollupMaterializer materializer,
            PlatformLeaderLockService leaderLockService,
            ClusterProperties clusterProperties,
            MssqlRelationalProperties mssqlRelationalProperties
    ) {
        return new JdbcMssqlHistorianRollupMaterializerRunner(
                materializer,
                leaderLockService,
                clusterProperties,
                mssqlRelationalProperties
        );
    }
}
