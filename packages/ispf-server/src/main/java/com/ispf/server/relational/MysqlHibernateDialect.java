package com.ispf.server.relational;

import org.hibernate.dialect.MySQLDialect;
import org.hibernate.engine.jdbc.dialect.spi.DialectResolutionInfo;
import org.hibernate.engine.jdbc.env.spi.IdentifierCaseStrategy;
import org.hibernate.engine.jdbc.env.spi.IdentifierHelper;
import org.hibernate.engine.jdbc.env.spi.IdentifierHelperBuilder;

import java.sql.DatabaseMetaData;
import java.sql.SQLException;
import java.sql.Types;

/**
 * Hibernate {@link MySQLDialect} for a MySQL {@code ISPF_DB} reached through MariaDB Connector/J (ADR-0079).
 * <p>Connector/J reports a stored identifier case only for {@code lower_case_table_names} 1 (lower) and
 * 2 (mixed). At 0, the Linux default, it reports none, and Hibernate keeps its upper-case default: schema
 * validation then looks names up in upper case on a case-sensitive server and finds no tables.
 * Seeding mixed case (as Hibernate's {@code MariaDBDialect} does) keeps names as written when the driver
 * is silent; a case the driver does report still wins.
 */
public class MysqlHibernateDialect extends MySQLDialect {

    public MysqlHibernateDialect() {
    }

    public MysqlHibernateDialect(DialectResolutionInfo info) {
        super(info);
    }

    @Override
    public IdentifierHelper buildIdentifierHelper(IdentifierHelperBuilder builder, DatabaseMetaData metadata)
            throws SQLException {
        builder.setUnquotedCaseStrategy(IdentifierCaseStrategy.MIXED);
        builder.setQuotedCaseStrategy(IdentifierCaseStrategy.MIXED);
        return super.buildIdentifierHelper(builder, metadata);
    }

    /**
     * {@code mysql/V1} stores PostgreSQL {@code BYTEA} as {@code LONGBLOB}; entities typed {@code BINARY}
     * (what {@code bytea} reports) must validate against it.
     */
    @Override
    public boolean equivalentTypes(int typeCode1, int typeCode2) {
        return super.equivalentTypes(typeCode1, typeCode2)
                || isBinaryStoredAsLongBlob(typeCode1, typeCode2)
                || isBinaryStoredAsLongBlob(typeCode2, typeCode1);
    }

    private static boolean isBinaryStoredAsLongBlob(int mapped, int stored) {
        return mapped == Types.BINARY && stored == Types.LONGVARBINARY;
    }
}
