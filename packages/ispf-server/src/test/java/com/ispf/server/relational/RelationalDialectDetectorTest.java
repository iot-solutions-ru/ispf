package com.ispf.server.relational;

import com.ispf.server.config.MetadataDbProperties;
import org.junit.jupiter.api.Test;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.SQLException;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

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

    @Test
    void refusesMariadbConfigAlias() {
        assertThatThrownBy(() -> RelationalDbKind.fromConfig("mariadb"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("ADR-0079");
        assertThat(RelationalDbKind.fromConfig("mysql")).isEqualTo(RelationalDbKind.MYSQL);
    }

    @Test
    void detectFromConnectionRefusesMariadbProduct() throws SQLException {
        assertThatThrownBy(() -> RelationalDialectDetector.detectFromConnection(metaConnection("MariaDB")))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("ADR-0079");
        assertThatThrownBy(() -> RelationalDialectDetector.detectFromConnection(metaConnection("MySQL / MariaDB")))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("ADR-0079");
        assertThat(RelationalDialectDetector.detectFromConnection(metaConnection("MySQL")))
                .isEqualTo(RelationalDbKind.MYSQL);
    }

    @Test
    void configuredMysqlStillRefusesMariadbServer() throws SQLException {
        MetadataDbProperties properties = new MetadataDbProperties();
        properties.setKind("mysql");
        RelationalDialectDetector detector = new RelationalDialectDetector(
                Map.of(RelationalDbKind.MYSQL, new MysqlDialect()), properties);

        assertThatThrownBy(() -> detector.resolve(metaDataSource("MariaDB")))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("ADR-0079");
        assertThat(detector.resolve(metaDataSource("MySQL")).kind()).isEqualTo(RelationalDbKind.MYSQL);
    }

    private static DataSource metaDataSource(String productName) throws SQLException {
        Connection connection = metaConnection(productName);
        DataSource dataSource = mock(DataSource.class);
        when(dataSource.getConnection()).thenReturn(connection);
        return dataSource;
    }

    private static Connection metaConnection(String productName) throws SQLException {
        Connection connection = mock(Connection.class);
        DatabaseMetaData meta = mock(DatabaseMetaData.class);
        when(connection.getMetaData()).thenReturn(meta);
        when(meta.getDatabaseProductName()).thenReturn(productName);
        return connection;
    }
}
