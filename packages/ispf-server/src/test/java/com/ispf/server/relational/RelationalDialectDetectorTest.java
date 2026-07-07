package com.ispf.server.relational;

import com.ispf.server.config.MetadataDbProperties;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class RelationalDialectDetectorTest {

    private static Map<RelationalDbKind, RelationalDialect> allDialects() {
        return Map.of(
                RelationalDbKind.POSTGRESQL, new PostgreSqlDialect(),
                RelationalDbKind.MSSQL, new MssqlDialect(),
                RelationalDbKind.H2, new H2Dialect()
        );
    }

    @Test
    void resolvesConfiguredKind() {
        MetadataDbProperties properties = new MetadataDbProperties();
        properties.setKind("mssql");
        RelationalDialectDetector detector = new RelationalDialectDetector(allDialects(), properties);
        RelationalDialect dialect = detector.resolve(null);
        assertThat(dialect.kind()).isEqualTo(RelationalDbKind.MSSQL);
    }

    @Test
    void postgresDialectExposesFlywayLocation() {
        PostgreSqlDialect dialect = new PostgreSqlDialect();
        assertThat(dialect.flywayLocations()).containsExactly("classpath:db/migration/postgresql");
        assertThat(dialect.queuedPlatformJobSelectSql()).contains("SKIP LOCKED");
    }

    @Test
    void mssqlDialectUsesReadPastLocking() {
        MssqlDialect dialect = new MssqlDialect();
        assertThat(dialect.queuedPlatformJobSelectSql()).contains("READPAST");
    }
}
