package com.ispf.plugin.workflow;

import java.time.Instant;
import java.util.Map;

/**
 * One durable execution-journal row for a workflow instance step (ADR-0049 Wave 1).
 */
public record WorkflowStepRecord(
        String tokenId,
        int seq,
        String nodeId,
        String nodeType,
        Instant startedAt,
        Instant endedAt,
        String status,
        int attempt,
        Map<String, Object> input,
        Map<String, Object> output,
        Map<String, Object> error
) {
    public static WorkflowStepRecord started(
            String tokenId,
            int seq,
            String nodeId,
            String nodeType,
            Map<String, Object> input,
            int attempt
    ) {
        return new WorkflowStepRecord(
                tokenId,
                seq,
                nodeId,
                nodeType,
                Instant.now(),
                null,
                "RUNNING",
                attempt,
                input == null ? Map.of() : Map.copyOf(input),
                Map.of(),
                Map.of()
        );
    }

    public WorkflowStepRecord completed(Map<String, Object> output) {
        return new WorkflowStepRecord(
                tokenId,
                seq,
                nodeId,
                nodeType,
                startedAt,
                Instant.now(),
                "COMPLETED",
                attempt,
                input,
                output == null ? Map.of() : Map.copyOf(output),
                Map.of()
        );
    }

    public WorkflowStepRecord failed(String message) {
        return new WorkflowStepRecord(
                tokenId,
                seq,
                nodeId,
                nodeType,
                startedAt,
                Instant.now(),
                "FAILED",
                attempt,
                input,
                output,
                Map.of("message", message == null ? "" : message)
        );
    }
}
