package com.ispf.server.platform.analytics.catalog;

import com.ispf.analytics.engine.AnalyticsDagBuilder;
import com.ispf.analytics.engine.AnalyticsEvaluationResult;
import com.ispf.analytics.engine.AnalyticsSourceRef;
import com.ispf.analytics.engine.AnalyticsTagDefinition;
import com.ispf.analytics.engine.HistorianTagPaths;
import com.ispf.core.object.PlatformObject;
import com.ispf.server.driver.DriverPointMappingParser;
import com.ispf.server.object.ObjectManager;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Historian tag Haystack sync and quality propagation via {@link HistorianRuleMetaService} (ADR-0041).
 */
@Service
public class AnalyticsTagMetadataService {

    public static final String QUALITY_OK = "ok";
    public static final String QUALITY_UNCERTAIN = "uncertain";
    public static final String QUALITY_ERROR = "error";
    public static final String QUALITY_DISABLED = "disabled";

    private static final Set<String> BAD_UPSTREAM_QUALITIES = Set.of(
            QUALITY_UNCERTAIN,
            QUALITY_ERROR,
            QUALITY_DISABLED
    );

    private final ObjectManager objectManager;
    private final HistorianRuleMetaService historianRuleMetaService;
    private final ObjectMapper objectMapper;

    public AnalyticsTagMetadataService(
            ObjectManager objectManager,
            HistorianRuleMetaService historianRuleMetaService,
            ObjectMapper objectMapper
    ) {
        this.objectManager = objectManager;
        this.historianRuleMetaService = historianRuleMetaService;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public void recordEvaluations(List<AnalyticsEvaluationResult> results, Instant observedAt) {
        for (AnalyticsEvaluationResult result : results) {
            historianRuleMetaService.recordEvaluation(result.tagPath(), result.status(), observedAt);
        }
    }

    @Transactional
    public void propagateQuality(List<AnalyticsTagDefinition> tags) {
        if (tags.isEmpty()) {
            return;
        }
        AnalyticsDagBuilder.AnalyticsTagAdjacency adjacency = AnalyticsDagBuilder.adjacency(tags);
        Map<String, String> qualityByPath = new LinkedHashMap<>();
        for (AnalyticsTagDefinition tag : tags) {
            PlatformObject node = objectManager.require(tag.objectPath());
            String quality = historianRuleMetaService.readRuleMeta(node, tag.ruleId()).quality();
            if (!tag.enabled()) {
                quality = QUALITY_DISABLED;
            }
            qualityByPath.put(tag.tagPath(), quality);
        }
        List<AnalyticsTagDefinition> ordered = AnalyticsDagBuilder.build(tags).orderedTags();
        for (AnalyticsTagDefinition tag : ordered) {
            String quality = qualityByPath.getOrDefault(tag.tagPath(), QUALITY_OK);
            for (String upstream : adjacency.upstreamByTag().getOrDefault(tag.tagPath(), Set.of())) {
                String upstreamQuality = qualityByPath.getOrDefault(upstream, QUALITY_OK);
                if (BAD_UPSTREAM_QUALITIES.contains(upstreamQuality)) {
                    quality = QUALITY_UNCERTAIN;
                    break;
                }
            }
            if (!quality.equals(qualityByPath.get(tag.tagPath()))) {
                historianRuleMetaService.setQuality(tag.tagPath(), quality);
                qualityByPath.put(tag.tagPath(), quality);
            }
        }
    }

    @Transactional
    public void syncHaystackForTag(AnalyticsTagDefinition tag) {
        String objectPath = HistorianTagPaths.objectPath(tag.tagPath());
        PlatformObject node = objectManager.require(objectPath);
        if (node.getVariable("driverPointMappingsJson").isEmpty()) {
            return;
        }
        String outputVariable = tag.outputVariable();
        String haystackTags = "point,cur,his";
        List<String> tags = List.of(haystackTags.split(",")).stream()
                .map(String::trim)
                .filter(item -> !item.isBlank())
                .toList();
        if (tags.isEmpty()) {
            return;
        }
        String mappingsJson = node.getVariable("driverPointMappingsJson")
                .flatMap(variable -> variable.value())
                .map(record -> String.valueOf(record.firstRow().get("value")))
                .orElse("{}");
        Map<String, DriverPointMappingParser.Entry> mappings = new LinkedHashMap<>(
                DriverPointMappingParser.parse(mappingsJson, objectMapper)
        );
        DriverPointMappingParser.Entry existing = mappings.get(outputVariable);
        mappings.put(outputVariable, new DriverPointMappingParser.Entry(
                existing != null ? existing.pointId() : "",
                tags,
                existing != null ? existing.unit() : "",
                existing != null ? existing.dis() : node.displayName()
        ));
        try {
            Map<String, Object> serialized = new LinkedHashMap<>();
            for (Map.Entry<String, DriverPointMappingParser.Entry> item : mappings.entrySet()) {
                DriverPointMappingParser.Entry entry = item.getValue();
                Map<String, Object> payload = new LinkedHashMap<>();
                if (!entry.pointId().isBlank()) {
                    payload.put("point", entry.pointId());
                }
                if (!entry.haystackTags().isEmpty()) {
                    payload.put("haystackTags", entry.haystackTags());
                }
                if (!entry.unit().isBlank()) {
                    payload.put("unit", entry.unit());
                }
                if (!entry.dis().isBlank()) {
                    payload.put("dis", entry.dis());
                }
                serialized.put(item.getKey(), payload.isEmpty() ? entry.pointId() : payload);
            }
            objectManager.setSystemVariableValue(
                    objectPath,
                    "driverPointMappingsJson",
                    com.ispf.core.model.DataRecord.single(
                            com.ispf.core.model.DataSchema.builder("stringValue")
                                    .field("value", com.ispf.core.model.FieldType.STRING)
                                    .build(),
                            Map.of("value", objectMapper.writeValueAsString(serialized))
                    )
            );
        } catch (Exception ex) {
            throw new IllegalStateException("Failed to serialize Haystack point mappings for " + objectPath, ex);
        }
    }

    public static String buildExpression(String helper, List<AnalyticsSourceRef> sources, String windowBucket) {
        String sourceRef = sources.isEmpty()
                ? "?"
                : sources.stream()
                        .map(source -> source.path() + "." + source.variable())
                        .reduce((left, right) -> left + ", " + right)
                        .orElse("?");
        return helper + "(" + sourceRef + ", " + windowBucket + ")";
    }

    public static List<String> upstreamTagPathsFromSources(
            List<AnalyticsTagDefinition> tags,
            List<AnalyticsSourceRef> sources
    ) {
        List<String> upstream = new ArrayList<>();
        for (AnalyticsSourceRef source : sources) {
            for (AnalyticsTagDefinition tag : tags) {
                if (tag.objectPath().equals(source.path())
                        && tag.outputVariable().equals(source.variable())) {
                    upstream.add(tag.tagPath());
                    break;
                }
            }
        }
        return List.copyOf(upstream);
    }
}
