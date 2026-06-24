package com.ispf.server.cache;

import com.ispf.server.object.ObjectChangeEvent;
import com.ispf.server.object.ObjectChangeType;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

@Component
public class ObjectChangeCacheInvalidator {

    private final CacheManager cacheManager;

    public ObjectChangeCacheInvalidator(CacheManager cacheManager) {
        this.cacheManager = cacheManager;
    }

    @EventListener
    public void onObjectChange(ObjectChangeEvent event) {
        if (event.type() == ObjectChangeType.CREATED
                || event.type() == ObjectChangeType.UPDATED
                || event.type() == ObjectChangeType.DELETED) {
            Cache briefingCache = cacheManager.getCache("platformBriefing");
            if (briefingCache != null) {
                briefingCache.clear();
            }
        }
    }
}
