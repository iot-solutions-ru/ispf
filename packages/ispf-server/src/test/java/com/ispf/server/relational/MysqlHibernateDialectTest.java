package com.ispf.server.relational;

import org.hibernate.boot.model.naming.Identifier;
import org.hibernate.cfg.AvailableSettings;
import org.hibernate.dialect.Dialect;
import org.hibernate.dialect.MySQLDialect;
import org.hibernate.engine.jdbc.env.spi.IdentifierCaseStrategy;
import org.hibernate.engine.jdbc.env.spi.IdentifierHelper;
import org.hibernate.engine.jdbc.env.spi.IdentifierHelperBuilder;
import org.hibernate.engine.jdbc.env.spi.JdbcEnvironment;
import org.junit.jupiter.api.Test;

import java.sql.DatabaseMetaData;
import java.sql.SQLException;
import java.sql.Types;
import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class MysqlHibernateDialectTest {

    private final RelationalConfiguration configuration = new RelationalConfiguration();

    @Test
    void pinsMysqlDialectBecauseMariadbDriverWouldSelectMariadbDialect() {
        Map<String, Object> properties = new HashMap<>();
        configuration.mysqlHibernateDialect(new MysqlDialect()).customize(properties);
        assertThat(properties).containsEntry(AvailableSettings.DIALECT, MysqlHibernateDialect.class.getName());
    }

    @Test
    void keepsAnExplicitDialect() {
        Map<String, Object> properties = new HashMap<>();
        properties.put(AvailableSettings.DIALECT, "com.example.CustomDialect");
        configuration.mysqlHibernateDialect(new MysqlDialect()).customize(properties);
        assertThat(properties).containsEntry(AvailableSettings.DIALECT, "com.example.CustomDialect");
    }

    @Test
    void leavesOtherEnginesToHibernateDetection() {
        Map<String, Object> properties = new HashMap<>();
        configuration.mysqlHibernateDialect(new PostgreSqlDialect()).customize(properties);
        configuration.mysqlHibernateDialect(new MssqlDialect()).customize(properties);
        assertThat(properties).doesNotContainKey(AvailableSettings.DIALECT);
    }

    @Test
    void looksNamesUpAsWrittenWhenConnectorReportsNoCase() throws SQLException {
        IdentifierHelper helper = identifierHelper(new MysqlHibernateDialect(), connectorMetadata(0));
        assertThat(helper.toMetaDataCatalogName(Identifier.toIdentifier("ispf"))).isEqualTo("ispf");
        assertThat(helper.toMetaDataObjectName(Identifier.toIdentifier("audit_events"))).isEqualTo("audit_events");
    }

    @Test
    void plainMysqlDialectUpperCasesNamesForTheSameServer() throws SQLException {
        IdentifierHelper helper = identifierHelper(new MySQLDialect(), connectorMetadata(0));
        assertThat(helper.toMetaDataCatalogName(Identifier.toIdentifier("ispf"))).isEqualTo("ISPF");
        assertThat(helper.toMetaDataObjectName(Identifier.toIdentifier("audit_events"))).isEqualTo("AUDIT_EVENTS");
    }

    @Test
    void caseReportedByConnectorStillWins() throws SQLException {
        assertThat(unquotedCase(connectorMetadata(1))).isEqualTo(IdentifierCaseStrategy.LOWER);
        assertThat(unquotedCase(connectorMetadata(2))).isEqualTo(IdentifierCaseStrategy.MIXED);
    }

    @Test
    void binaryMappingValidatesAgainstLongBlob() {
        MysqlHibernateDialect dialect = new MysqlHibernateDialect();
        assertThat(new MySQLDialect().equivalentTypes(Types.BINARY, Types.LONGVARBINARY)).isFalse();
        assertThat(dialect.equivalentTypes(Types.BINARY, Types.LONGVARBINARY)).isTrue();
        assertThat(dialect.equivalentTypes(Types.LONGVARBINARY, Types.BINARY)).isTrue();
        assertThat(dialect.equivalentTypes(Types.BINARY, Types.BLOB)).isFalse();
        assertThat(dialect.equivalentTypes(Types.BINARY, Types.LONGVARCHAR)).isFalse();
    }

    /**
     * MariaDB Connector/J 3.5 answers for a server's {@code lower_case_table_names}: lower only for 1,
     * mixed only for 2, never upper; quoted identifiers report the same.
     */
    private static DatabaseMetaData connectorMetadata(int lowerCaseTableNames) throws SQLException {
        DatabaseMetaData metadata = mock(DatabaseMetaData.class);
        when(metadata.storesLowerCaseIdentifiers()).thenReturn(lowerCaseTableNames == 1);
        when(metadata.storesMixedCaseIdentifiers()).thenReturn(lowerCaseTableNames == 2);
        when(metadata.storesLowerCaseQuotedIdentifiers()).thenReturn(lowerCaseTableNames == 1);
        when(metadata.storesMixedCaseQuotedIdentifiers()).thenReturn(lowerCaseTableNames == 2);
        return metadata;
    }

    private static IdentifierHelper identifierHelper(Dialect dialect, DatabaseMetaData metadata) throws SQLException {
        return dialect.buildIdentifierHelper(IdentifierHelperBuilder.from(mock(JdbcEnvironment.class)), metadata);
    }

    private static IdentifierCaseStrategy unquotedCase(DatabaseMetaData metadata) throws SQLException {
        IdentifierHelperBuilder builder = IdentifierHelperBuilder.from(mock(JdbcEnvironment.class));
        new MysqlHibernateDialect().buildIdentifierHelper(builder, metadata);
        return builder.getUnquotedCaseStrategy();
    }
}
