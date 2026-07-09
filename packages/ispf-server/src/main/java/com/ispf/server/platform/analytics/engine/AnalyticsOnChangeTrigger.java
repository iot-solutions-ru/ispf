package com.ispf.server.platform.analytics.engine;

import com.ispf.analytics.engine.AnalyticsTagDefinition;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Coalesced on-change analytics evaluation when historian sources update (BL-203).
 */
@Component
public class AnalyticsOnChangeTrigger {

    private static final long COALESCE_MS = 250L;

    private final AnalyticsEngineService engineService;
    private final AnalyticsTagCatalogService catalogService;
    private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
        Thread thread = new Thread(r, "analytics-on-change");
        thread.setDaemon(true);
        return thread;
    });
    private final Map<String, AtomicBoolean> scheduled = new ConcurrentHashMap<>();

    public AnalyticsOnChangeTrigger(
            AnalyticsEngineService engineService,
            AnalyticsTagCatalogService catalogService
    ) {
        this.engineService = engineService;
        this.catalogService = catalogService;
    }

    public void onSourceSample(String objectPath, String variableName) {
        if (!engineService.isEnabled()) {
            return;
        }
        String key = objectPath + "|" + variableName;
        AtomicBoolean flag = scheduled.computeIfAbsent(key, ignored -> new AtomicBoolean(false));
        if (!flag.compareAndSet(false, true)) {
            return;
        }
        scheduler.schedule(() -> {
            try {
                var tags = catalogService.tagsAffectedBySource(objectPath, variableName);
                if (!tags.isEmpty()) {
                    engineService.evaluateTags(tags);
                }
            } finally {
                flag.set(false);
            }
        }, COALESCE_MS, TimeUnit.MILLISECONDS);
    }
}
