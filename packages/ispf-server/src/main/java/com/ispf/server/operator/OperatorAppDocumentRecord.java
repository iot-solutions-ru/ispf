package com.ispf.server.operator;

import java.time.Instant;

public record OperatorAppDocumentRecord(
        String docId,
        String appId,
        String filename,
        String mimeType,
        String description,
        String contentText,
        long byteSize,
        Instant createdAt,
        Instant updatedAt
) {
}
