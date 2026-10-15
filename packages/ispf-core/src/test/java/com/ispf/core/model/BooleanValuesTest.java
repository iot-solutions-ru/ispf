package com.ispf.core.model;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class BooleanValuesTest {

    @Test
    void acceptsBooleanAsIs() {
        assertThat(BooleanValues.requireBoolean("flag", true)).isTrue();
        assertThat(BooleanValues.requireBoolean("flag", false)).isFalse();
    }

    @Test
    void acceptsZeroAndOneNumbers() {
        assertThat(BooleanValues.requireBoolean("flag", 0)).isFalse();
        assertThat(BooleanValues.requireBoolean("flag", 1)).isTrue();
        assertThat(BooleanValues.requireBoolean("flag", 1L)).isTrue();
        assertThat(BooleanValues.requireBoolean("flag", 0.0)).isFalse();
        assertThat(BooleanValues.requireBoolean("flag", 1.0)).isTrue();
    }

    @Test
    void rejectsOtherNumbers() {
        assertThatThrownBy(() -> BooleanValues.requireBoolean("flag", 42))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("must be boolean");
        assertThatThrownBy(() -> BooleanValues.requireBoolean("flag", 0.5))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("must be boolean");
        assertThatThrownBy(() -> BooleanValues.requireBoolean("flag", Double.NaN))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("must be boolean");
    }

    @Test
    void acceptsExplicitStringContract() {
        assertThat(BooleanValues.requireBoolean("flag", "true")).isTrue();
        assertThat(BooleanValues.requireBoolean("flag", " FALSE ")).isFalse();
        assertThat(BooleanValues.requireBoolean("flag", "1")).isTrue();
        assertThat(BooleanValues.requireBoolean("flag", "0")).isFalse();
    }

    @Test
    void rejectsOtherStringsAndTypes() {
        assertThatThrownBy(() -> BooleanValues.requireBoolean("flag", "yes"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("must be boolean");
        assertThatThrownBy(() -> BooleanValues.requireBoolean("flag", new Object()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("must be boolean");
    }
}
