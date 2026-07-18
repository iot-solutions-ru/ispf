package com.ispf.server.cache;

import com.ispf.server.cache.PlatformBriefingCacheEpoch;
import com.ispf.server.object.ObjectChangeEvent;
import com.ispf.server.object.ObjectChangeType;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

@Component
public class ObjectChangeCacheInvalidator {

    private final PlatformBriefingCacheEpoch briefingCacheEpoch;

    public ObjectChangeCacheInvalidator(PlatformBriefingCacheEpoch briefingCacheEpoch) {
        this.briefingCacheEpoch = briefingCacheEpoch;
    }

    @EventListener
    public void onObjectChange(ObjectChangeEvent event) {
        if (event.type() == ObjectChangeType.CREATED
                || event.type() == ObjectChangeType.UPDATED
                || event.type() == ObjectChangeType.DELETED) {
            briefingCacheEpoch.bump();
        }
    }
}
