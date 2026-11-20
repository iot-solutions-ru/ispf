package com.ispf.server.relational;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class MssqlHistorianDivergenceTest {

    @Test
    void greenfieldKeyIncludesPartitionColumnAndIsNotApplied() {
        assertThat(MssqlHistorianDivergence.NOT_APPLIED).contains("Not applied");
        assertThat(MssqlHistorianDivergence.greenfieldPrimaryKeySketch()).contains("sampled_at");
        assertThat(MssqlHistorianDivergence.rollupTableSketch()).contains("variable_sample_rollups");
    }
}
