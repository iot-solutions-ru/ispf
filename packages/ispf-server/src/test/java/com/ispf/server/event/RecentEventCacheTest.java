package com.ispf.server.event;

import com.ispf.core.model.DataRecord;
import com.ispf.core.model.DataSchema;
import com.ispf.core.object.EventLevel;
import com.ispf.core.object.ObjectEvent;
import com.ispf.server.config.EventJournalProperties;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class RecentEventCacheTest {

    @Test
    void purgeOlderThanDropsMatchingCacheEntriesInOnePass() {
        EventJournalProperties properties = new EventJournalProperties();
        properties.setRecentCacheSize(50);
        RecentEventCache cache = new RecentEventCache(properties);
        Instant cutoff = Instant.parse("2024-01-01T00:00:00Z");
        DataRecord payload = DataRecord.empty(DataSchema.builder("empty").build());

        cache.append(event("root.a", Instant.parse("2020-01-01T00:00:00Z"), payload));
        cache.append(event("root.a.child", Instant.parse("2020-06-01T00:00:00Z"), payload));
        cache.append(event("root.a", Instant.parse("2026-01-01T00:00:00Z"), payload));
        cache.append(event("root.b", Instant.parse("2020-01-01T00:00:00Z"), payload));

        cache.purgeOlderThan(cutoff, "root.a");

        List<ObjectEvent> remaining = cache.query(null, 20);
        assertEquals(2, remaining.size());
        assertEquals("root.b", remaining.get(0).objectPath());
        assertEquals("root.a", remaining.get(1).objectPath());
    }

    private static ObjectEvent event(String path, Instant timestamp, DataRecord payload) {
        return new ObjectEvent("id-" + path + "-" + timestamp, path, "ping", EventLevel.INFO, payload, timestamp);
    }
}
