package com.ispf.server.history;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.core.annotation.Order;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import javax.sql.DataSource;
import java.sql.Connection;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Converts {@code variable_samples} to a TimescaleDB hypertable when the extension is available.
 */
@Component
public class TimescaleHypertableInitializer {

    private static final Logger log = LoggerFactory.getLogger(TimescaleHypertableInitializer.class);

    private final JdbcTemplate jdbcTemplate;
    private final AtomicBoolean timescaleRetentionActive = new AtomicBoolean(false);

    public TimescaleHypertableInitializer(DataSource dataSource) {
        this.jdbcTemplate = new JdbcTemplate(dataSource);
    }

    public boolean isTimescaleRetentionActive() {
        return timescaleRetentionActive.get();
    }

    @EventListener(ApplicationReadyEvent.class)
    @Order(120)
    public void ensureHypertable() {
        if (!isPostgreSql()) {
            return;
        }
        try {
            Boolean hasExtension = jdbcTemplate.queryForObject("""
                    SELECT EXISTS (
                        SELECT 1 FROM pg_extension WHERE extname = 'timescaledb'
                    )
                    """, Boolean.class);
            if (hasExtension == null || !hasExtension) {
                log.info("TimescaleDB extension not installed — using application retention purge");
                return;
            }

            jdbcTemplate.execute("ALTER TABLE variable_samples DROP CONSTRAINT IF EXISTS variable_samples_pkey");
            jdbcTemplate.execute("ALTER TABLE variable_samples ADD PRIMARY KEY (sampled_at, id)");

            jdbcTemplate.execute("""
                    SELECT create_hypertable(
                        'variable_samples',
                        'sampled_at',
                        if_not_exists => TRUE,
                        migrate_data => TRUE
                    )
                    """);

            jdbcTemplate.execute("""
                    SELECT add_retention_policy(
                        'variable_samples',
                        INTERVAL '90 days',
                        if_not_exists => TRUE
                    )
                    """);

            timescaleRetentionActive.set(true);
            log.info("TimescaleDB hypertable and retention policy enabled for variable_samples");
        } catch (Exception ex) {
            log.info("TimescaleDB hypertable setup skipped: {}", ex.getMessage());
        }
    }

    private boolean isPostgreSql() {
        try (Connection connection = jdbcTemplate.getDataSource().getConnection()) {
            String product = connection.getMetaData().getDatabaseProductName();
            return product != null && product.toLowerCase().contains("postgresql");
        } catch (Exception ex) {
            return false;
        }
    }
}
