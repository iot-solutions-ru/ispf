package com.ispf.server.platform.analytics.frames;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

public record EventFrame(
        UUID frameId,
        EventFrameType frameType,
        String scopePath,
        String sourcePath,
        String sourceKey,
        String label,
        Instant startedAt,
        Instant endedAt,
        int downtimeMinutes,
        Map<String, String> metadata
) {
    public boolean active() {
        return endedAt == null;
    }

    public Instant effectiveEnd(Instant now) {
        return endedAt != null ? endedAt : now;
    }
}
