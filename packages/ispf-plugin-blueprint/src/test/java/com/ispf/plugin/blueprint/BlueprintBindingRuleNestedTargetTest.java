package com.ispf.plugin.blueprint;

import com.ispf.core.binding.BindingTarget;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class BlueprintBindingRuleNestedTargetTest {

    @Test
    void nestedTargetFillsFlatVariableAndField() {
        BlueprintBindingRule rule = new BlueprintBindingRule(
                "pulse",
                "pulse",
                true,
                0,
                null,
                null,
                "1",
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                new BindingTarget("status", "value")
        );

        assertThat(rule.targetVariable()).isEqualTo("status");
        assertThat(rule.targetField()).isEqualTo("value");
        assertThat(rule.toBindingRule().target().variableName()).isEqualTo("status");
    }

    @Test
    void flatFieldsWinOverNestedTarget() {
        BlueprintBindingRule rule = new BlueprintBindingRule(
                "pulse",
                "pulse",
                true,
                0,
                null,
                null,
                "1",
                "flatVar",
                "amount",
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                new BindingTarget("nestedVar", "value")
        );

        assertThat(rule.targetVariable()).isEqualTo("flatVar");
        assertThat(rule.targetField()).isEqualTo("amount");
    }
}
