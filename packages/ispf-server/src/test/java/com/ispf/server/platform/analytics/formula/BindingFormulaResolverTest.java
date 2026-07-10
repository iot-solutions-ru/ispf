package com.ispf.server.platform.analytics.formula;

import com.ispf.core.binding.BindingActivators;
import com.ispf.core.binding.BindingRule;
import com.ispf.core.binding.BindingRuleKind;
import com.ispf.core.binding.BindingTarget;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class BindingFormulaResolverTest {

    @Mock
    private AnalyticsFormulaService formulaService;

    @InjectMocks
    private BindingFormulaResolver resolver;

    @Test
    void expandsFormulaRefIntoExpression() {
        BindingRule rule = new BindingRule(
                "rule-a",
                "rule-a",
                true,
                0,
                BindingRuleKind.HISTORIAN,
                new BindingActivators(false, null, null, 60_000L, false, false),
                "",
                "stale",
                new BindingTarget("variable", "avg-a", "value", null, null),
                "1h",
                null,
                "tank-fill-rate",
                Map.of("levelPath", "root.dev.tank.level", "tankArea", "2"),
                AnalyticsFormula.SCOPE_SITE,
                null
        );
        when(formulaService.expand(
                "tank-fill-rate",
                rule.formulaParams(),
                AnalyticsFormula.SCOPE_SITE,
                null
        )).thenReturn("rateOfChange(root.dev.tank.level, 1h) * 2");

        BindingRule resolved = resolver.resolve(rule);

        assertThat(resolved.expression()).isEqualTo("rateOfChange(root.dev.tank.level, 1h) * 2");
        assertThat(resolved.formulaRef()).isEqualTo("tank-fill-rate");
    }
}
