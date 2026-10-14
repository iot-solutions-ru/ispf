package com.ispf.server.history;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class VariableHistorySnapshotTest {

    @Test
    void keepsSnapshotThatFits() {
        assertThat(VariableHistoryService.requireCompleteSnapshot("{\"note\":\"ok\"}"))
                .isEqualTo("{\"note\":\"ok\"}");
    }

    @Test
    void rejectsSnapshotLongerThanLimit() {
        String json = "x".repeat(65_537);

        assertThatThrownBy(() -> VariableHistoryService.requireCompleteSnapshot(json))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("record snapshot exceeds 65536 characters");
    }

    @Test
    void rejectsMissingSnapshot() {
        assertThatThrownBy(() -> VariableHistoryService.requireCompleteSnapshot(null))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("record snapshot serialization failed");
    }
}
