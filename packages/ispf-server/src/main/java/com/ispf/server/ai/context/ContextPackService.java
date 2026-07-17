package com.ispf.server.ai.context;

import com.ispf.server.config.AiProperties;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.stereotype.Service;
import tools.jackson.databind.ObjectMapper;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class ContextPackService {

    private static final String CACHE_NAME = "contextPack";
    private static final String CACHE_KEY = "default";
    private static final int TOP_GAPS = 5;

    private final AiProperties properties;
    private final ResourceLoader resourceLoader;
    private final ObjectMapper objectMapper;
    private final CacheManager cacheManager;
    private final ContextPackLiveOverlayService liveOverlayService;
    private final PlatformBriefingCacheEpoch briefingCacheEpoch;

    public ContextPackService(
            AiProperties properties,
            ResourceLoader resourceLoader,
            ObjectMapper objectMapper,
            CacheManager cacheManager,
            ContextPackLiveOverlayService liveOverlayService,
            PlatformBriefingCacheEpoch briefingCacheEpoch
    ) {
        this.properties = properties;
        this.resourceLoader = resourceLoader;
        this.objectMapper = objectMapper;
        this.cacheManager = cacheManager;
        this.liveOverlayService = liveOverlayService;
        this.briefingCacheEpoch = briefingCacheEpoch;
    }

    public Map<String, Object> info() {
        Map<String, Object> pack = loadPack();
        Map<String, Object> info = new LinkedHashMap<>();
        info.put("contextPackVersion", pack.getOrDefault("contextPackVersion", "unknown"));
        info.put("platformVersion", pack.getOrDefault("platformVersion", "unknown"));
        info.put("generatedAt", pack.getOrDefault("generatedAt", null));
        info.put("contentSha256", pack.getOrDefault("contentSha256", null));
        info.put("exampleCount", pack.get("examples") instanceof List<?> list ? list.size() : 0);
        info.put("scriptStepCount", pack.get("scriptSteps") instanceof List<?> list ? list.size() : 0);
        info.put("widgetTypeCount", pack.get("widgetTypes") instanceof List<?> list ? list.size() : 0);
        List<Map<String, Object>> gaps = competitiveGaps(pack);
        info.put("competitiveGapCount", gaps.size());
        info.put("topReadinessGaps", topGaps(gaps, TOP_GAPS));
        info.put("livePlatform", liveOverlayService.snapshot());
        return info;
    }

    /**
     * BL-182: evict classpath pack cache and bump live overlay epoch.
     */
    public Map<String, Object> refresh() {
        evictCache();
        briefingCacheEpoch.bump();
        Map<String, Object> result = new LinkedHashMap<>(info());
        result.put("status", "OK");
        result.put("refreshed", true);
        return result;
    }

    public void evictCache() {
        Cache cache = cacheManager.getCache(CACHE_NAME);
        if (cache != null) {
            cache.evict(CACHE_KEY);
        }
    }

    @SuppressWarnings("unchecked")
    public Map<String, Object> loadPack() {
        Cache cache = cacheManager.getCache(CACHE_NAME);
        if (cache == null) {
            return readPack();
        }
        Map<String, Object> cached = cache.get(CACHE_KEY, Map.class);
        if (cached != null) {
            return cached;
        }
        Map<String, Object> loaded = readPack();
        cache.put(CACHE_KEY, loaded);
        return loaded;
    }

    public String contextPackVersion() {
        Object version = loadPack().get("contextPackVersion");
        return version != null ? String.valueOf(version) : "unknown";
    }

    @SuppressWarnings("unchecked")
    static List<Map<String, Object>> competitiveGaps(Map<String, Object> pack) {
        Object index = pack.get("competitiveGapIndex");
        if (!(index instanceof List<?> list)) {
            return List.of();
        }
        List<Map<String, Object>> gaps = new ArrayList<>();
        for (Object item : list) {
            if (item instanceof Map<?, ?> row) {
                Map<String, Object> copy = new LinkedHashMap<>();
                for (Map.Entry<?, ?> entry : row.entrySet()) {
                    copy.put(String.valueOf(entry.getKey()), entry.getValue());
                }
                gaps.add(copy);
            }
        }
        return gaps;
    }

    static List<Map<String, Object>> topGaps(List<Map<String, Object>> gaps, int limit) {
        return gaps.stream()
                .sorted(Comparator.comparingDouble(ContextPackService::gapValue).reversed())
                .limit(Math.max(0, limit))
                .map(row -> {
                    Map<String, Object> slim = new LinkedHashMap<>();
                    slim.put("dimension", row.get("dimension"));
                    slim.put("gap", row.get("gap"));
                    slim.put("current", row.get("current"));
                    slim.put("target", row.get("target"));
                    slim.put("phaseRef", row.get("phaseRef"));
                    return slim;
                })
                .toList();
    }

    private static double gapValue(Map<String, Object> row) {
        Object gap = row.get("gap");
        if (gap instanceof Number number) {
            return number.doubleValue();
        }
        try {
            return Double.parseDouble(String.valueOf(gap));
        } catch (Exception ignored) {
            return 0.0;
        }
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> readPack() {
        String location = properties.getContextPackClasspath();
        if (location == null || location.isBlank()) {
            location = "classpath:ai/context-pack.json";
        }
        Resource resource = resourceLoader.getResource(location);
        if (!resource.exists()) {
            return Map.of(
                    "contextPackVersion", "missing",
                    "generationPolicy", Map.of(
                            "allowedArtifacts", List.of("bundle"),
                            "forbidden", List.of("java in ispf-server")
                    )
            );
        }
        try (InputStream in = resource.getInputStream()) {
            return objectMapper.readValue(in, Map.class);
        } catch (Exception ex) {
            throw new IllegalStateException("Failed to load context pack from " + location + ": " + ex.getMessage(), ex);
        }
    }
}
