package com.ispf.core.model;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class IntegerValuesTest {

    @Test
    void acceptsWholeNumbersInIntRange() {
        assertThat(IntegerValues.requireInt("n", 7)).isEqualTo(7);
        assertThat(IntegerValues.requireInt("n", 7L)).isEqualTo(7);
        assertThat(IntegerValues.requireInt("n", 7.0)).isEqualTo(7);
        assertThat(IntegerValues.requireInt("n", "7")).isEqualTo(7);
    }

    @Test
    void rejectsFractionInsteadOfTruncating() {
        assertThatThrownBy(() -> IntegerValues.requireInt("n", 1.9))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("must be integer");
    }

    @Test
    void rejectsOutOfIntRange() {
        assertThatThrownBy(() -> IntegerValues.requireInt("n", Integer.MAX_VALUE + 1L))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("out of integer range");
    }
}
