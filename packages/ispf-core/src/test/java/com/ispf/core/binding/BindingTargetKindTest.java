package com.ispf.core.binding;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class BindingTargetKindTest {

    @Test
    void blankDefaultsToVariable() {
        assertThat(BindingTargetKind.normalize(null)).isEqualTo(BindingTargetKind.VARIABLE);
        assertThat(BindingTargetKind.normalize("  ")).isEqualTo(BindingTargetKind.VARIABLE);
    }

    @Test
    void normalizesKnownKinds() {
        assertThat(BindingTargetKind.normalize("Variable")).isEqualTo(BindingTargetKind.VARIABLE);
        assertThat(BindingTargetKind.normalize("CONTEXT")).isEqualTo(BindingTargetKind.CONTEXT);
        assertThat(BindingTargetKind.normalize("event")).isEqualTo(BindingTargetKind.EVENT);
        assertThat(BindingTargetKind.normalize("action")).isEqualTo(BindingTargetKind.ACTION);
    }

    @Test
    void rejectsUnknownKind() {
        assertThatThrownBy(() -> BindingTargetKind.normalize("varible"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Unknown binding target kind");
    }
}
