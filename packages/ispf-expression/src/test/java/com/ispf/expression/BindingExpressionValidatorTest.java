package com.ispf.expression;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class BindingExpressionValidatorTest {

    @Test
    void acceptsCounterRateBinding() {
        assertThatCode(() -> BindingExpressionValidator.validateOrThrow("counterRate(ifInOctets)"))
                .doesNotThrowAnyException();
        assertThatCode(() -> BindingExpressionValidator.validateOrThrow("counterRate(source, 4294967296, value)"))
                .doesNotThrowAnyException();
    }

    @Test
    void acceptsPhaseOnePlatformBindings() {
        assertThatCode(() -> BindingExpressionValidator.validateOrThrow("selectField(temperature)"))
                .doesNotThrowAnyException();
        assertThatCode(() -> BindingExpressionValidator.validateOrThrow("scale(temperature, -20, 50, 0, 100)"))
                .doesNotThrowAnyException();
        assertThatCode(() -> BindingExpressionValidator.validateOrThrow("clamp(temperature, 0, 50)"))
                .doesNotThrowAnyException();
        assertThatCode(() -> BindingExpressionValidator.validateOrThrow("format(\"%.1f °C\", temperature)"))
                .doesNotThrowAnyException();
        assertThatCode(() -> BindingExpressionValidator.validateOrThrow("delta(counter)"))
                .doesNotThrowAnyException();
    }

    @Test
    void acceptsNewPlatformBindings() {
        assertThatCode(() -> BindingExpressionValidator.validateOrThrow("rate(gauge)"))
                .doesNotThrowAnyException();
        assertThatCode(() -> BindingExpressionValidator.validateOrThrow("movingAvg(gauge, 60)"))
                .doesNotThrowAnyException();
        assertThatCode(() -> BindingExpressionValidator.validateOrThrow("refAt(\"root.platform.devices.foo\", temperature)"))
                .doesNotThrowAnyException();
        assertThatCode(() -> BindingExpressionValidator.validateOrThrow("callFunctionAt(\"root.remote\", fn, input)"))
                .doesNotThrowAnyException();
    }

    @Test
    void acceptsCelExpression() {
        assertThatCode(() -> BindingExpressionValidator.validateOrThrow("self.temperature.value + 1.0"))
                .doesNotThrowAnyException();
        assertThatCode(() -> BindingExpressionValidator.validateOrThrow("true"))
                .doesNotThrowAnyException();
    }

    @Test
    void rejectsInvalidExpression() {
        assertThatThrownBy(() -> BindingExpressionValidator.validateOrThrow("not valid cel +++"))
                .isInstanceOf(Exception.class);
    }
}
