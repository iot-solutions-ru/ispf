package com.ispf.server.relational;

import com.ispf.server.config.MetadataDbProperties;
import com.ispf.server.config.OracleJdbcDriverEnvironmentPostProcessor;
import org.hibernate.cfg.AvailableSettings;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.boot.hibernate.autoconfigure.HibernatePropertiesCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;

import javax.sql.DataSource;
import java.util.EnumMap;
import java.util.Map;

@Configuration
public class RelationalConfiguration {

    @Bean
    Map<RelationalDbKind, RelationalDialect> relationalDialectImplementations() {
        EnumMap<RelationalDbKind, RelationalDialect> dialects = new EnumMap<>(RelationalDbKind.class);
        dialects.put(RelationalDbKind.POSTGRESQL, new PostgreSqlDialect());
        dialects.put(RelationalDbKind.H2, new H2Dialect());
        dialects.put(RelationalDbKind.MSSQL, new MssqlDialect());
        dialects.put(RelationalDbKind.MYSQL, new MysqlDialect());
        dialects.put(RelationalDbKind.ORACLE, new OracleDialect());
        return Map.copyOf(dialects);
    }

    @Bean
    RelationalDialectDetector relationalDialectDetector(
            Map<RelationalDbKind, RelationalDialect> relationalDialectImplementations,
            MetadataDbProperties metadataDbProperties
    ) {
        return new RelationalDialectDetector(relationalDialectImplementations, metadataDbProperties);
    }

    @Bean
    RelationalDialect relationalDialect(DataSource dataSource, RelationalDialectDetector detector) {
        return detector.resolve(dataSource);
    }

    /**
     * ADR-0079: Hibernate resolves any driver named "MariaDB…" to MariaDBDialect, even against a
     * MySQL server (client != server), so pin a MySQLDialect on a MySQL ISPF_DB. The pinned subclass
     * also makes {@code ddl-auto=validate} pass ({@link MysqlHibernateDialect}).
     */
    @Bean
    HibernatePropertiesCustomizer mysqlHibernateDialect(RelationalDialect relationalDialect) {
        return properties -> {
            if (relationalDialect.kind() == RelationalDbKind.MYSQL) {
                properties.putIfAbsent(AvailableSettings.DIALECT, MysqlHibernateDialect.class.getName());
            }
        };
    }

    /**
     * ADR-0080: Hibernate binds Instant as TIMESTAMP_UTC and reads it back as OffsetDateTime, which
     * ojdbc refuses for a TIMESTAMP column (ORA-18716). Bind plain TIMESTAMP on Oracle: the same
     * JVM-zone wall time ISPF's JDBC code writes with {@code Timestamp.from(instant)}.
     */
    @Bean
    HibernatePropertiesCustomizer oracleHibernateInstants(RelationalDialect relationalDialect) {
        return properties -> {
            if (relationalDialect.kind() == RelationalDbKind.ORACLE) {
                properties.putIfAbsent(AvailableSettings.PREFERRED_INSTANT_JDBC_TYPE, "TIMESTAMP");
            }
        };
    }

    /**
     * ADR-0080: on an Oracle ISPF_DB, JDBC code binds java.util.UUID values that ojdbc rejects
     * (ORA-17004). Only the primary DataSource of an Oracle deployment is wrapped.
     */
    @Bean
    static BeanPostProcessor oracleUuidJdbcDataSource(Environment environment) {
        return new BeanPostProcessor() {
            @Override
            public Object postProcessAfterInitialization(Object bean, String beanName) {
                if ("dataSource".equals(beanName)
                        && bean instanceof DataSource dataSource
                        && !(bean instanceof OracleUuidJdbcDataSource)
                        && OracleJdbcDriverEnvironmentPostProcessor.isOracleMetadata(environment)) {
                    return new OracleUuidJdbcDataSource(dataSource);
                }
                return bean;
            }
        };
    }
}
