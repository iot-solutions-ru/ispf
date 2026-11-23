package com.ispf.server.config;

import com.ispf.server.relational.RelationalDbKind;
import com.ispf.server.relational.RelationalDialect;
import org.springframework.context.event.ContextRefreshedEvent;
import org.springframework.context.event.EventListener;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * Runs only when {@code ISPF_MYSQL_SINGLE_ENGINE=true}. B1 boots skip this.
 */
@Component
public class MysqlDatabasePreflightRunner {

    private final RelationalDialect relationalDialect;
    private final MysqlRelationalProperties mysqlRelationalProperties;
    private final JdbcTemplate jdbcTemplate;

    public MysqlDatabasePreflightRunner(
            RelationalDialect relationalDialect,
            MysqlRelationalProperties mysqlRelationalProperties,
            JdbcTemplate jdbcTemplate
    ) {
        this.relationalDialect = relationalDialect;
        this.mysqlRelationalProperties = mysqlRelationalProperties;
        this.jdbcTemplate = jdbcTemplate;
    }

    @EventListener(ContextRefreshedEvent.class)
    public void assertDatabaseReady() {
        if (relationalDialect.kind() != RelationalDbKind.MYSQL || !mysqlRelationalProperties.isSingleEngine()) {
            return;
        }
        // Session value: what ISPF pool connections actually run, including a pool-level override.
        String isolation = jdbcTemplate.queryForObject("SELECT @@SESSION.transaction_isolation", String.class);
        String collation = jdbcTemplate.queryForObject(
                "SELECT default_collation_name FROM information_schema.schemata WHERE schema_name = DATABASE()",
                String.class
        );
        MysqlDatabasePreflight.assertReady(isolation, collation);
    }
}
