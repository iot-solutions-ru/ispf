package com.ispf.core.object;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class HistoryAndStorageModeParseTest {

    @Test
    void historySampleModeParseIsStrict() {
        assertThat(HistorySampleMode.parse(null)).isEqualTo(HistorySampleMode.CHANGES_ONLY);
        assertThat(HistorySampleMode.parse("ALL_VALUES")).isEqualTo(HistorySampleMode.ALL_VALUES);
        assertThatThrownBy(() -> HistorySampleMode.parse("not-a-mode"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void historySampleModeParseLenientFallsBack() {
        assertThat(HistorySampleMode.parseLenient("not-a-mode")).isEqualTo(HistorySampleMode.CHANGES_ONLY);
    }

    @Test
    void variableStorageModeParseIsStrict() {
        assertThat(VariableStorageMode.parse(null)).isEqualTo(VariableStorageMode.PERSISTENT);
        assertThat(VariableStorageMode.parse("TRANSIENT")).isEqualTo(VariableStorageMode.TRANSIENT);
        assertThatThrownBy(() -> VariableStorageMode.parse("not-a-mode"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void variableStorageModeParseLenientFallsBack() {
        assertThat(VariableStorageMode.parseLenient("not-a-mode")).isEqualTo(VariableStorageMode.PERSISTENT);
    }
}
