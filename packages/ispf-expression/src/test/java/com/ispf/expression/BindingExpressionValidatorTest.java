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
