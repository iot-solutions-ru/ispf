package com.ispf.server.ai.agent;

import java.time.Instant;

public record AgentSessionDocumentRecord(
        String docId,
        String sessionId,
        String filename,
        String mimeType,
        String description,
        String contentText,
        long byteSize,
        Instant createdAt,
        Instant updatedAt
) {
}
