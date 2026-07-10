package com.ispf.server.platform.analytics.formula;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AnalyticsFormulaExpanderTest {

    @Test
    void detectsPlaceholders() {
        assertThat(AnalyticsFormulaExpander.detectParameterNames(
                "rateOfChange({{levelPath}}, 1h) * {{tankArea}}"
        )).containsExactly("levelPath", "tankArea");
    }

    @Test
    void expandsPlaceholders() {
        String expanded = AnalyticsFormulaExpander.expand(
                "rateOfChange({{levelPath}}, 1h) * {{tankArea}}",
                Map.of(
                        "levelPath", "root.platform.devices.tank-01.level",
                        "tankArea", "12.5"
                )
        );
        assertThat(expanded).isEqualTo("rateOfChange(root.platform.devices.tank-01.level, 1h) * 12.5");
    }

    @Test
    void rejectsMissingParameter() {
        assertThatThrownBy(() -> AnalyticsFormulaExpander.expand(
                "rollingAvg({{source}}, 5m)",
                Map.of()
        )).isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("source");
    }
}
