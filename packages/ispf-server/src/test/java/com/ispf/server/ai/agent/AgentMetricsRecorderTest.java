package com.ispf.server.ai.agent;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class AgentMetricsRecorderTest {

    @Test
    void snapshotTracksTurnsStepsAndGuardBlocks() {
        AgentMetricsRecorder recorder = new AgentMetricsRecorder(java.util.Optional.empty());
        recorder.recordTurnStarted();
        recorder.recordTurnStarted();
        recorder.recordTurnCompleted(4);
        recorder.recordRateLimited();
        recorder.recordGuardBlock("planGuard");
        recorder.recordGuardBlock("mutateApproval");

        var snapshot = recorder.agentSnapshot();
        assertThat(snapshot.get("turnsLastHour")).isEqualTo(2);
        assertThat(snapshot.get("turnsCompletedTotal")).isEqualTo(1L);
        assertThat(snapshot.get("avgStepsPerTurn")).isEqualTo(4.0);

        @SuppressWarnings("unchecked")
        var guardBlocks = (java.util.Map<String, Object>) snapshot.get("guardBlocksByType");
        assertThat(guardBlocks).containsEntry("planGuard", 1L);
        assertThat(guardBlocks).containsEntry("mutateApproval", 1L);
    }
}
