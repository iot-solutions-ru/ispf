package com.ispf.server.platform.analytics.engine;

import com.ispf.analytics.engine.AnalyticsTagDefinition;
import com.ispf.core.binding.BindingActivators;
import com.ispf.core.binding.BindingRule;
import com.ispf.core.binding.BindingRuleKind;
import com.ispf.core.binding.BindingTarget;
import com.ispf.core.binding.BindingVariableRef;
import com.ispf.server.config.AnalyticsProperties;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class HistorianBindingRuleCompilerTest {

    @Test
    void compilesRollingAvgBuiltin() {
        BindingRule rule = new BindingRule(
                "rule-a",
                "rule-a",
                true,
                0,
                BindingRuleKind.HISTORIAN,
                new BindingActivators(false, List.of(new BindingVariableRef("root.dev.a", "temperature")), null, 60_000L, false, false),
                "",
                "rollingAvg(root.platform.devices.demo-sensor-01.temperature, 1h)",
                new BindingTarget("variable", "avg-a", "value", null, null),
                "1h",
                null
        );
        AnalyticsProperties properties = new AnalyticsProperties(
                60_000L, true, true, 60_000L, false, 60_000L, 7, 20, 3_000L, 0
        );
        Optional<AnalyticsTagDefinition> compiled = HistorianBindingRuleCompiler.compile(
                "root.platform.devices.analytics-catalog-a",
                rule,
                properties
        );
        assertThat(compiled).isPresent();
        assertThat(compiled.get().helper()).isEqualTo("rollingAvg");
    }
}
