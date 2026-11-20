package com.ispf.server.config;

import com.ispf.server.relational.RelationalDialect;
import org.springframework.context.event.ContextRefreshedEvent;
import org.springframework.context.event.EventListener;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/**
 * ADR-0078 lab gate fences for MSSQL metadata (also enforced pre-Flyway via
 * {@link FlywayDialectConfiguration} → {@link MetadataDialectBootPolicy}).
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 20)
public class MssqlCertificationBootGuard {

    private final RelationalDialect relationalDialect;
    private final TenantIsolationProperties tenantIsolationProperties;
    private final VariableHistoryProperties variableHistoryProperties;
    private final EventJournalProperties eventJournalProperties;
    private final ClusterProperties clusterProperties;
    private final MssqlRelationalProperties mssqlRelationalProperties;
    private final HistorianTierProperties historianTierProperties;
    private final HistorianColdArchiveProperties coldArchiveProperties;

    public MssqlCertificationBootGuard(
            RelationalDialect relationalDialect,
            TenantIsolationProperties tenantIsolationProperties,
            VariableHistoryProperties variableHistoryProperties,
            EventJournalProperties eventJournalProperties,
            ClusterProperties clusterProperties,
            MssqlRelationalProperties mssqlRelationalProperties,
            HistorianTierProperties historianTierProperties,
            HistorianColdArchiveProperties coldArchiveProperties
    ) {
        this.relationalDialect = relationalDialect;
        this.tenantIsolationProperties = tenantIsolationProperties;
        this.variableHistoryProperties = variableHistoryProperties;
        this.eventJournalProperties = eventJournalProperties;
        this.clusterProperties = clusterProperties;
        this.mssqlRelationalProperties = mssqlRelationalProperties;
        this.historianTierProperties = historianTierProperties;
        this.coldArchiveProperties = coldArchiveProperties;
    }

    @EventListener(ContextRefreshedEvent.class)
    public void refuseUnsupportedMssqlModes() {
        MetadataDialectBootPolicy.assertMssqlB1Fences(
                relationalDialect,
                tenantIsolationProperties,
                variableHistoryProperties,
                eventJournalProperties,
                clusterProperties,
                mssqlRelationalProperties.isSingleEngine(),
                historianTierProperties.warmTier().isEnabled(),
                coldArchiveProperties.enabled()
        );
    }
}
