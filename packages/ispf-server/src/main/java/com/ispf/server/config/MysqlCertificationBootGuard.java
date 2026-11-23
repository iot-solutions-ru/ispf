package com.ispf.server.config;

import com.ispf.server.relational.RelationalDialect;
import org.springframework.context.event.ContextRefreshedEvent;
import org.springframework.context.event.EventListener;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/**
 * ADR-0079 fences for MySQL metadata (also enforced pre-Flyway via
 * {@link com.ispf.server.relational.FlywayDialectConfiguration} → {@link MetadataDialectBootPolicy}).
 * Runs even when Flyway is disabled, so a MySQL ISPF_DB never boots outside B1 or the opt-in
 * single-engine profile (B2, and A-A with cluster on).
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 21)
public class MysqlCertificationBootGuard {

    private final RelationalDialect relationalDialect;
    private final TenantIsolationProperties tenantIsolationProperties;
    private final VariableHistoryProperties variableHistoryProperties;
    private final EventJournalProperties eventJournalProperties;
    private final ClusterProperties clusterProperties;
    private final MysqlRelationalProperties mysqlRelationalProperties;
    private final HistorianTierProperties historianTierProperties;
    private final HistorianColdArchiveProperties coldArchiveProperties;

    public MysqlCertificationBootGuard(
            RelationalDialect relationalDialect,
            TenantIsolationProperties tenantIsolationProperties,
            VariableHistoryProperties variableHistoryProperties,
            EventJournalProperties eventJournalProperties,
            ClusterProperties clusterProperties,
            MysqlRelationalProperties mysqlRelationalProperties,
            HistorianTierProperties historianTierProperties,
            HistorianColdArchiveProperties coldArchiveProperties
    ) {
        this.relationalDialect = relationalDialect;
        this.tenantIsolationProperties = tenantIsolationProperties;
        this.variableHistoryProperties = variableHistoryProperties;
        this.eventJournalProperties = eventJournalProperties;
        this.clusterProperties = clusterProperties;
        this.mysqlRelationalProperties = mysqlRelationalProperties;
        this.historianTierProperties = historianTierProperties;
        this.coldArchiveProperties = coldArchiveProperties;
    }

    @EventListener(ContextRefreshedEvent.class)
    public void refuseUnsupportedMysqlModes() {
        MetadataDialectBootPolicy.assertMysqlB1Fences(
                relationalDialect,
                tenantIsolationProperties,
                variableHistoryProperties,
                eventJournalProperties,
                clusterProperties,
                mysqlRelationalProperties.isSingleEngine(),
                historianTierProperties.warmTier().isEnabled(),
                coldArchiveProperties.enabled()
        );
    }
}
