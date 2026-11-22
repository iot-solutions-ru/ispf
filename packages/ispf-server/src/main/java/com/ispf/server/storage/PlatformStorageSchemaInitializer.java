package com.ispf.server.storage;

import com.ispf.server.config.EventJournalProperties;
import com.ispf.server.config.NatsProperties;
import com.ispf.server.config.VariableHistoryProperties;
import com.ispf.server.cluster.NatsJetStreamSupport;
import com.ispf.server.relational.RelationalDbKind;
import com.ispf.server.relational.RelationalDialect;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.ContextRefreshedEvent;
import org.springframework.context.event.EventListener;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import javax.sql.DataSource;

/**
 * Verifies platform relational schema (Flyway) and logs active time-series backends after
 * per-store {@code ensureSchema()} hooks complete on empty or recreated databases.
 */
@Component
public class PlatformStorageSchemaInitializer {

    private static final Logger log = LoggerFactory.getLogger(PlatformStorageSchemaInitializer.class);

    private final JdbcTemplate jdbcTemplate;
    private final RelationalDialect relationalDialect;
    private final EventJournalProperties eventJournalProperties;
    private final VariableHistoryProperties variableHistoryProperties;
    private final NatsProperties natsProperties;
    private final NatsJetStreamSupport jetStreamSupport;

    public PlatformStorageSchemaInitializer(
            DataSource dataSource,
            RelationalDialect relationalDialect,
            EventJournalProperties eventJournalProperties,
            VariableHistoryProperties variableHistoryProperties,
            NatsProperties natsProperties,
            NatsJetStreamSupport jetStreamSupport
    ) {
        this.jdbcTemplate = new JdbcTemplate(dataSource);
        this.relationalDialect = relationalDialect;
        this.eventJournalProperties = eventJournalProperties;
        this.variableHistoryProperties = variableHistoryProperties;
        this.natsProperties = natsProperties;
        this.jetStreamSupport = jetStreamSupport;
    }

    @EventListener(ContextRefreshedEvent.class)
    public void verifyPlatformSchema(ContextRefreshedEvent event) {
        if (event.getApplicationContext().getParent() != null) {
            return;
        }
        verifyRelationalCatalog();
        logStorageBackends();
    }

    private void verifyRelationalCatalog() {
        StorageStartupRetry.run("Platform relational catalog", () -> {
            // Quoted identifiers stay lowercase on H2 MODE=PostgreSQL without DATABASE_TO_LOWER;
            // the dialect quotes them (MySQL rejects ANSI double quotes without ANSI_QUOTES).
            // Flyway quotes its own table verbatim, so on Oracle it is the lowercase name.
            String history = relationalDialect.kind() == RelationalDbKind.ORACLE
                    ? "\"flyway_schema_history\""
                    : relationalDialect.quoteIdentifier("flyway_schema_history");
            Integer migrationCount = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM " + history, Integer.class);
            if (migrationCount == null || migrationCount <= 0) {
                throw new IllegalStateException("Flyway migrations have not been applied");
            }
            jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM " + relationalDialect.quoteIdentifier("object_nodes"),
                    Integer.class
            );
        });
        log.info("Platform relational schema verified (Flyway migrations applied)");
    }

    private void logStorageBackends() {
        log.info(
                "Event journal store={} variable-history store={} jetStreamReady={}",
                eventJournalProperties.getStore(),
                variableHistoryProperties.getStore(),
                natsProperties.jetStreamEnabled() && jetStreamSupport.isStreamReady()
        );
    }
}
