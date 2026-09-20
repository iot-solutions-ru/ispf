package com.ispf.server.ai.context;

import com.ispf.server.cache.PlatformBriefingCacheEpoch;
import com.ispf.server.config.AiProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.cache.concurrent.ConcurrentMapCacheManager;
import org.springframework.core.io.DefaultResourceLoader;
import tools.jackson.databind.ObjectMapper;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

/**
 * F-06: load the embedded context pack without a full Spring Boot context.
 */
@ExtendWith(MockitoExtension.class)
class ContextPackBuildTest {

    @Mock
    private ContextPackLiveOverlayService liveOverlayService;

    @Mock
    private PlatformBriefingCacheEpoch briefingCacheEpoch;

    private ContextPackService contextPackService;

    @BeforeEach
    void setUp() {
        contextPackService = new ContextPackService(
                new AiProperties(),
                new DefaultResourceLoader(),
                new ObjectMapper(),
                new ConcurrentMapCacheManager("contextPack"),
                liveOverlayService,
                briefingCacheEpoch
        );
    }

    @Test
    void contextPackHasStructuredIndices() {
        Map<String, Object> pack = contextPackService.loadPack();

        assertFalse(String.valueOf(pack.get("contextPackVersion")).contains("0.1.0-SNAPSHOT"));
        assertTrue(pack.get("driverCatalog") instanceof List<?> drivers && drivers.size() >= 50);
        assertTrue(pack.get("exampleSummaries") instanceof List<?> examples && !examples.isEmpty());
        assertTrue(pack.get("featureIndex") instanceof List<?> features && !features.isEmpty());
        assertTrue(pack.get("docChunks") instanceof List<?> chunks && !chunks.isEmpty());
        assertTrue(pack.get("docCatalog") instanceof List<?> catalog && !catalog.isEmpty());
        assertTrue(pack.get("competitiveGapIndex") instanceof List<?> gaps && gaps.size() >= 5);
        Object firstGap = ((List<?>) pack.get("competitiveGapIndex")).getFirst();
        assertTrue(firstGap instanceof Map<?, ?> row
                && row.containsKey("dimension")
                && row.containsKey("gap"));
    }

    @Test
    void contextPackInfoExposesGapsAndLiveOverlay() {
        when(liveOverlayService.snapshot()).thenReturn(Map.of(
                "driverCount", 50,
                "cacheEpoch", 1L
        ));

        Map<String, Object> info = contextPackService.info();
        assertTrue(((Number) info.get("competitiveGapCount")).intValue() >= 5);
        assertTrue(info.get("topReadinessGaps") instanceof List<?> top && !top.isEmpty());
        assertTrue(info.get("livePlatform") instanceof Map<?, ?> live
                && live.containsKey("driverCount")
                && live.containsKey("cacheEpoch"));
    }
}
