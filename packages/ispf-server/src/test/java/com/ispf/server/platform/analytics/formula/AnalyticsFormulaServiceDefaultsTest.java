package com.ispf.server.platform.analytics.formula;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AnalyticsFormulaServiceDefaultsTest {

    @Test
    void missingOptionalParameterUsesDefaultValue() {
        Map<String, String> resolved = AnalyticsFormulaService.withDefaults(
                List.of(
                        new AnalyticsFormulaParameter("tempPath", "tagPath", true, null, null),
                        new AnalyticsFormulaParameter("factor", "number", false, null, "2")
                ),
                Map.of("tempPath", "root.platform.devices.coldchain-control.room-a/temperature")
        );

        String expanded = AnalyticsFormulaExpander.expand(
                "avg({{tempPath}}, 5m) * {{factor}}",
                resolved
        );

        assertThat(expanded).isEqualTo(
                "avg(root.platform.devices.coldchain-control.room-a/temperature, 5m) * 2"
        );
    }

    @Test
    void suppliedValueOverridesDefault() {
        Map<String, String> resolved = AnalyticsFormulaService.withDefaults(
                List.of(new AnalyticsFormulaParameter("factor", "number", false, null, "2")),
                Map.of("factor", "3")
        );

        assertThat(resolved).containsEntry("factor", "3");
    }

    @Test
    void missingRequiredParameterWithoutDefaultStillFails() {
        Map<String, String> resolved = AnalyticsFormulaService.withDefaults(
                List.of(new AnalyticsFormulaParameter("tempPath", "tagPath", true, null, null)),
                Map.of()
        );

        assertThatThrownBy(() -> AnalyticsFormulaExpander.expand("avg({{tempPath}}, 5m)", resolved))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("tempPath");
    }
}
