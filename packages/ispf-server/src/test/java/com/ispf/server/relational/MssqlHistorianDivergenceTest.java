package com.ispf.server.relational;

import org.junit.jupiter.api.Test;

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
    }
}
