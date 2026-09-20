package com.ispf.server.relational;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * F-06: Flyway + H2 dialect smoke without {@code @SpringBootTest}.
 */
class RelationalFlywayMigrationTest {

    @Test
    void flywayMigrationsAppliedOnH2() {
        H2Dialect dialect = new H2Dialect();
        assertThat(dialect.kind()).isEqualTo(RelationalDbKind.H2);

        Flyway flyway = Flyway.configure()
                .dataSource(
                        "jdbc:h2:mem:ispf-flyway-f06;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;"
                                + "DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE;DEFAULT_NULL_ORDERING=HIGH",
                        "sa",
                        ""
                )
                .locations(dialect.flywayLocations())
                .placeholders(Map.of(
                        "rls_block_start", "/*",
                        "rls_block_end", "*/"
                ))
                .load();
        flyway.migrate();

        assertThat(flyway.info().applied()).isNotEmpty();
        assertThat(flyway.info().pending()).isEmpty();
    }

    @Test
    void h2DialectLoadsPostgresqlPack() {
        assertThat(new H2Dialect().flywayLocations())
                .contains("classpath:db/migration/postgresql");
    }
}
