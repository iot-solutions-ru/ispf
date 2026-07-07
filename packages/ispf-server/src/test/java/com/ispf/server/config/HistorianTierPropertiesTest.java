package com.ispf.server.config;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class HistorianTierPropertiesTest {

    @Test
    void defaultTiers_includeHotWarmCold() {
        HistorianTierProperties properties = new HistorianTierProperties();
        assertThat(properties.getTiers()).containsKeys("hot", "warm", "cold");
        assertThat(properties.hotTier().isJdbcStore()).isTrue();
        assertThat(properties.hotTier().isDualWriteEnabled()).isTrue();
        assertThat(properties.warmTier().isClickHouseStore()).isTrue();
        assertThat(properties.coldTier().isColdArchiveStore()).isTrue();
    }

    @Test
    void variableHistorySlo_defaultsMatchBl161() {
        VariableHistorySloProperties slo = new VariableHistorySloProperties();
        assertThat(slo.getAggregateMaxPoints()).isEqualTo(1_000_000L);
        assertThat(slo.getAggregateMaxLatencyMs()).isEqualTo(2_000L);
    }
}
