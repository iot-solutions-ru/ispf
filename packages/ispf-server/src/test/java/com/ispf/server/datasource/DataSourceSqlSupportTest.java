package com.ispf.server.datasource;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class DataSourceSqlSupportTest {

    @Test
    void detectsReadQueries() {
        assertThat(DataSourceSqlSupport.isReadQuery("SELECT 1")).isTrue();
        assertThat(DataSourceSqlSupport.isReadQuery("  with x as (select 1) select * from x")).isTrue();
        assertThat(DataSourceSqlSupport.isReadQuery("INSERT INTO t VALUES (1)")).isFalse();
        assertThat(DataSourceSqlSupport.isReadQuery("UPDATE t SET a = 1")).isFalse();
    }

    @Test
    void rejectsMultiStatementSql() {
        assertThatThrownBy(() -> DataSourceSqlSupport.normalizeSql("SELECT 1; SELECT 2"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("single SQL statement");
    }

    @Test
    void stripsTrailingSemicolon() {
        assertThat(DataSourceSqlSupport.normalizeSql("SELECT 1;")).isEqualTo("SELECT 1");
    }

    @Test
    void normalizesParams() {
        assertThat(DataSourceSqlSupport.normalizeParams(null)).isEmpty();
        assertThat(DataSourceSqlSupport.normalizeParams(List.of("a", 1))).containsExactly("a", 1);
    }
}
