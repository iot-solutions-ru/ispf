package com.ispf.server.relational;

import com.ispf.server.config.ClusterProperties;
import com.ispf.server.config.EventJournalProperties;
import com.ispf.server.config.HistorianColdArchiveProperties;
import com.ispf.server.config.HistorianTierProperties;
import com.ispf.server.config.MetadataDialectBootPolicy;
import com.ispf.server.config.MssqlRelationalProperties;
import com.ispf.server.config.MysqlRelationalProperties;
import com.ispf.server.config.TenantIsolationProperties;
import com.ispf.server.config.VariableHistoryProperties;
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
            RelationalDialectDetector relationalDialectDetector,
            TenantIsolationProperties tenantIsolationProperties,
            VariableHistoryProperties variableHistoryProperties,
            EventJournalProperties eventJournalProperties,
            ClusterProperties clusterProperties,
            MssqlRelationalProperties mssqlRelationalProperties,
            MysqlRelationalProperties mysqlRelationalProperties,
            HistorianTierProperties historianTierProperties,
            HistorianColdArchiveProperties coldArchiveProperties
    ) {
        return configuration -> {
            RelationalDialect dialect = relationalDialectDetector.resolve(dataSource);
            // Refuse before Flyway locations are applied / migrate runs.
            MetadataDialectBootPolicy.assertEngineCertified(dialect);
            MetadataDialectBootPolicy.assertOracleB1Fences(
                    dialect,
                    tenantIsolationProperties,
                    variableHistoryProperties,
                    eventJournalProperties,
                    clusterProperties
            );
            MetadataDialectBootPolicy.assertMssqlB1Fences(
                    dialect,
                    tenantIsolationProperties,
                    variableHistoryProperties,
                    eventJournalProperties,
                    clusterProperties,
                    mssqlRelationalProperties.isSingleEngine(),
                    historianTierProperties.warmTier().isEnabled(),
                    coldArchiveProperties.enabled()
            );
            MetadataDialectBootPolicy.assertMysqlB1Fences(
                    dialect,
                    tenantIsolationProperties,
                    variableHistoryProperties,
                    eventJournalProperties,
                    clusterProperties,
                    mysqlRelationalProperties.isSingleEngine(),
                    historianTierProperties.warmTier().isEnabled(),
                    coldArchiveProperties.enabled()
            );
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
