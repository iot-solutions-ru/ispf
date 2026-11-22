package com.ispf.server.config;

import com.ispf.server.relational.RelationalDialect;
import org.springframework.context.event.ContextRefreshedEvent;
import org.springframework.context.event.EventListener;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/**
 * ADR-0080 B1 fences for Oracle metadata (also enforced pre-Flyway via
 * {@link com.ispf.server.relational.FlywayDialectConfiguration} → {@link MetadataDialectBootPolicy}).
 * Runs even when Flyway is disabled, so an Oracle ISPF_DB never boots outside B1.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 22)
public class OracleCertificationBootGuard {

    private final RelationalDialect relationalDialect;
    private final TenantIsolationProperties tenantIsolationProperties;
    private final VariableHistoryProperties variableHistoryProperties;
    private final EventJournalProperties eventJournalProperties;
    private final ClusterProperties clusterProperties;

    public OracleCertificationBootGuard(
            RelationalDialect relationalDialect,
            TenantIsolationProperties tenantIsolationProperties,
            VariableHistoryProperties variableHistoryProperties,
            EventJournalProperties eventJournalProperties,
            ClusterProperties clusterProperties
    ) {
        this.relationalDialect = relationalDialect;
        this.tenantIsolationProperties = tenantIsolationProperties;
        this.variableHistoryProperties = variableHistoryProperties;
        this.eventJournalProperties = eventJournalProperties;
        this.clusterProperties = clusterProperties;
    }

    @EventListener(ContextRefreshedEvent.class)
    public void refuseUnsupportedOracleModes() {
        MetadataDialectBootPolicy.assertOracleB1Fences(
                relationalDialect,
                tenantIsolationProperties,
                variableHistoryProperties,
                eventJournalProperties,
                clusterProperties
        );
    }
}
