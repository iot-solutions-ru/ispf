package com.ispf.server.operator;

import java.time.Instant;

public record OperatorAgentMemoryRecord(
        String memoryId,
        String appId,
        String kind,
        String topic,
        String content,
        String sourceActor,
        String sourceTurnId,
        int useCount,
        Instant createdAt,
        Instant updatedAt
) {
}
