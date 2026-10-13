package com.ispf.server.history;

import com.ispf.server.config.HistorianTierProperties;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ClickHouseWarmTierAppendTest {

    @Test
    void appendBestEffortPropagatesClickHouseFailure() {
        HistorianTierProperties tiers = new HistorianTierProperties();
        tiers.warmTier().setEnabled(true);
        tiers.warmTier().setStore("clickhouse");
        tiers.warmTier().getClickhouse().setUrl("http://127.0.0.1:1");
        ClickHouseWarmTierWriteStore store = new ClickHouseWarmTierWriteStore(tiers);
        Instant now = Instant.parse("2026-07-06T12:00:00Z");
        VariableHistoryWriteRecord record = new VariableHistoryWriteRecord(
                "root.platform.devices.demo",
                "temperature",
                "value",
                now,
                now,
                21.5,
                null
        );

        assertThatThrownBy(() -> store.appendBestEffort(List.of(record)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("ClickHouse request failed");
    }
}
