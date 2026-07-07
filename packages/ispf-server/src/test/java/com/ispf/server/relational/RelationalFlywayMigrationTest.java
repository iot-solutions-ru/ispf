package com.ispf.server.relational;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * CI smoke: H2 test profile applies Flyway baseline from dialect-selected locations.
 */
@SpringBootTest
@ActiveProfiles("test")
class RelationalFlywayMigrationTest {

    @Autowired
    private Flyway flyway;

    @Autowired
    private RelationalDialect relationalDialect;

    @Test
    void flywayMigrationsAppliedOnH2() {
        assertThat(relationalDialect.kind()).isEqualTo(RelationalDbKind.H2);
        assertThat(flyway.info().applied()).isNotEmpty();
        assertThat(flyway.info().pending()).isEmpty();
    }

    @Test
    void h2DialectLoadsPostgresqlPack() {
        assertThat(relationalDialect.flywayLocations())
                .contains("classpath:db/migration/postgresql");
    }
}
