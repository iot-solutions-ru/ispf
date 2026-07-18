package com.ispf.server.cache;

import com.ispf.server.cache.PlatformBriefingCacheEpoch;
import com.ispf.server.object.ObjectChangeEvent;
import com.ispf.server.object.ObjectChangeType;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ObjectChangeCacheInvalidatorTest {

    @Test
    void bumpsBriefingEpochOnStructureChangeWithoutRedisKeys() {
        PlatformBriefingCacheEpoch epoch = new PlatformBriefingCacheEpoch();
        ObjectChangeCacheInvalidator invalidator = new ObjectChangeCacheInvalidator(epoch);

        invalidator.onObjectChange(ObjectChangeEvent.of(ObjectChangeType.UPDATED, "root.platform.devices.d1"));

        assertEquals(1L, epoch.current());
    }

    @Test
    void ignoresVariableUpdated() {
        PlatformBriefingCacheEpoch epoch = new PlatformBriefingCacheEpoch();
        ObjectChangeCacheInvalidator invalidator = new ObjectChangeCacheInvalidator(epoch);

        invalidator.onObjectChange(ObjectChangeEvent.variableUpdated(
                "root.platform.devices.d1",
                "temperature"
        ));

        assertEquals(0L, epoch.current());
    }
}
