package com.ispf.server.relational;

import org.hibernate.cfg.AvailableSettings;
import org.hibernate.dialect.MySQLDialect;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class MysqlHibernateDialectTest {

    private final RelationalConfiguration configuration = new RelationalConfiguration();

    @Test
    void pinsMysqlDialectBecauseMariadbDriverWouldSelectMariadbDialect() {
        Map<String, Object> properties = new HashMap<>();
        configuration.mysqlHibernateDialect(new MysqlDialect()).customize(properties);
        assertThat(properties).containsEntry(AvailableSettings.DIALECT, MySQLDialect.class.getName());
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
}
