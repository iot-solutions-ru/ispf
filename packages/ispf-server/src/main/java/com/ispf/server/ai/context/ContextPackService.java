package com.ispf.server.ai.context;

import com.ispf.server.config.AiProperties;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.stereotype.Service;
import tools.jackson.databind.ObjectMapper;

import java.io.InputStream;
import java.util.LinkedHashMap;
import java.util.Map;

@Service
public class ContextPackService {

    private static final String CACHE_NAME = "contextPack";
    private static final String CACHE_KEY = "default";

    private final AiProperties properties;
    private final ResourceLoader resourceLoader;
    private final ObjectMapper objectMapper;
    private final CacheManager cacheManager;

    public ContextPackService(
            AiProperties properties,
            ResourceLoader resourceLoader,
            ObjectMapper objectMapper,
            CacheManager cacheManager
    ) {
        this.properties = properties;
        this.resourceLoader = resourceLoader;
        this.objectMapper = objectMapper;
        this.cacheManager = cacheManager;
    }

    public Map<String, Object> info() {
        Map<String, Object> pack = loadPack();
        Map<String, Object> info = new LinkedHashMap<>();
        info.put("contextPackVersion", pack.getOrDefault("contextPackVersion", "unknown"));
        info.put("platformVersion", pack.getOrDefault("platformVersion", "unknown"));
        info.put("generatedAt", pack.getOrDefault("generatedAt", null));
        info.put("contentSha256", pack.getOrDefault("contentSha256", null));
        info.put("exampleCount", pack.get("examples") instanceof java.util.List<?> list ? list.size() : 0);
        info.put("scriptStepCount", pack.get("scriptSteps") instanceof java.util.List<?> list ? list.size() : 0);
        info.put("widgetTypeCount", pack.get("widgetTypes") instanceof java.util.List<?> list ? list.size() : 0);
        return info;
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
                            "allowedArtifacts", java.util.List.of("bundle"),
                            "forbidden", java.util.List.of("java in ispf-server")
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
