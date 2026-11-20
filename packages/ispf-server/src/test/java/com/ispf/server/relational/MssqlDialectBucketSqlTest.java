package com.ispf.server.relational;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class MssqlDialectBucketSqlTest {

    @Test
    void bucketSqlIsDateBucketOnObservedAt() {
        String sql = new MssqlDialect().variableSampleBucketAggregateSql();
        assertThat(sql).contains("DATE_BUCKET");
        assertThat(sql).contains("observed_at");
        assertThat(sql).contains("GROUP BY bucket_start");
        assertThat(sql).doesNotContain("COALESCE");
        assertThat(sql).doesNotContain("DATEDIFF");
        // Interval placeholder appears once — duplicated ? breaks MSSQL GROUP BY matching.
        assertThat(sql.split("\\?", -1).length - 1).isEqualTo(7);
    }
}
