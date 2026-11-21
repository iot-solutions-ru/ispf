package com.ispf.server.relational;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Locale;

import static org.assertj.core.api.Assertions.assertThat;

class MssqlHistorianDivergenceTest {

    @Test
    void greenfieldKeyIncludesPartitionColumnAndIsNotApplied() {
        assertThat(MssqlHistorianDivergence.NOT_APPLIED).contains("not applied");
        assertThat(MssqlHistorianDivergence.greenfieldPrimaryKeySketch()).contains("sampled_at");
        assertThat(MssqlHistorianDivergence.rollupTableSketch()).contains("variable_sample_rollups");
        assertThat(MssqlHistorianDivergence.V101_1_FOUNDATION).contains("variable_sample_rollups");
        assertThat(MssqlHistorianDivergence.V101_2_PARTITIONS).contains("APPLIED");
        assertThat(MssqlHistorianDivergence.STATUS_SKIPPED_NONEMPTY).isEqualTo("SKIPPED_NONEMPTY");
        assertThat(MssqlHistorianDivergence.V101_3_ROLLUP_CURSOR).contains("rollup_cursor");
        assertThat(MssqlHistorianDivergence.V101_4_OBSERVED_ROLLUPS).contains("observed_rollups");
        assertThat(MssqlHistorianDivergence.V101_5_SWITCH_STAGING).contains("switch_staging");
    }

    @Test
    void v1015StagingSqlIsEmptyPrimaryDdlWithoutSwitch() throws IOException {
        String sql;
        try (InputStream in = getClass().getClassLoader().getResourceAsStream(
                "db/migration/mssql/V101.5__variable_samples_switch_staging.sql")) {
            assertThat(in).as("V101.5 on classpath").isNotNull();
            sql = new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
        assertThat(sql).contains("variable_samples_switch_staging");
        assertThat(sql).contains("IDENTITY");
        assertThat(sql).contains("quality");
        assertThat(sql).contains("PRIMARY KEY CLUSTERED (sampled_at, id)");
        assertThat(sql).contains("ON [PRIMARY]");
        assertThat(sql).contains("uq_variable_samples_switch_staging_series_time");
        assertThat(sql).contains("idx_variable_samples_switch_staging_lookup");

        String executable = sql.lines()
                .map(String::strip)
                .filter(line -> !line.isEmpty() && !line.startsWith("--"))
                .reduce("", (a, b) -> a + "\n" + b)
                .toUpperCase(Locale.ROOT);
        assertThat(executable).doesNotContain(" SWITCH ");
        assertThat(executable).doesNotContain("TRUNCATE");
        assertThat(executable).doesNotContain("ALTER PARTITION");
    }
}
