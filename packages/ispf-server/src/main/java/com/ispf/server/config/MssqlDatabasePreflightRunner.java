package com.ispf.server.config;

import com.ispf.server.relational.RelationalDbKind;
import com.ispf.server.relational.RelationalDialect;
import org.springframework.context.event.ContextRefreshedEvent;
import org.springframework.context.event.EventListener;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * Runs only when {@code ISPF_MSSQL_SINGLE_ENGINE=true}. B1 lab boots skip this.
 */
@Component
public class MssqlDatabasePreflightRunner {

    private final RelationalDialect relationalDialect;
    private final MssqlRelationalProperties mssqlRelationalProperties;
    private final JdbcTemplate jdbcTemplate;

    public MssqlDatabasePreflightRunner(
            RelationalDialect relationalDialect,
            MssqlRelationalProperties mssqlRelationalProperties,
            JdbcTemplate jdbcTemplate
    ) {
        this.relationalDialect = relationalDialect;
        this.mssqlRelationalProperties = mssqlRelationalProperties;
        this.jdbcTemplate = jdbcTemplate;
    }

    @EventListener(ContextRefreshedEvent.class)
    public void assertDatabaseReady() {
        if (relationalDialect.kind() != RelationalDbKind.MSSQL || !mssqlRelationalProperties.isSingleEngine()) {
            return;
        }
        Integer snapshot = jdbcTemplate.queryForObject(
                "SELECT is_read_committed_snapshot_on FROM sys.databases WHERE name = DB_NAME()",
                Integer.class
        );
        String collation = jdbcTemplate.queryForObject(
                "SELECT collation_name FROM sys.databases WHERE name = DB_NAME()",
                String.class
        );
        MssqlDatabasePreflight.assertReady(snapshot != null && snapshot == 1, collation);
    }
}
