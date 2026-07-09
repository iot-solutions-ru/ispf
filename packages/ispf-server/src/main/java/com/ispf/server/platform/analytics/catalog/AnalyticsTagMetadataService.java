package com.ispf.server.platform.analytics.catalog;

import com.ispf.analytics.engine.AnalyticsDagBuilder;
import com.ispf.analytics.engine.AnalyticsDagBuilder;
import com.ispf.analytics.engine.AnalyticsEvaluationResult;
import com.ispf.analytics.engine.AnalyticsSourceRef;
import com.ispf.analytics.engine.AnalyticsTagDefinition;
import com.ispf.core.model.DataRecord;
import com.ispf.core.model.DataSchema;
import com.ispf.core.model.FieldType;
import com.ispf.core.object.ObjectType;
import com.ispf.core.object.PlatformObject;
import com.ispf.core.object.Variable;
import com.ispf.plugin.blueprint.BlueprintDefinition;
import com.ispf.plugin.blueprint.BlueprintRegistry;
import com.ispf.server.config.AnalyticsProperties;
import com.ispf.server.driver.DriverPointMappingParser;
import com.ispf.server.object.ObjectManager;
import com.ispf.server.platform.analytics.AnalyticsBlueprintBootstrap;
import com.ispf.server.platform.analytics.engine.AnalyticsTagCatalogService;
import com.ispf.server.plugin.blueprint.BlueprintApplicationService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Syncs analytics-tag-v1 metadata, Haystack point tags, and quality propagation (BL-209).
 */
@Service
public class AnalyticsTagMetadataService {

    public static final String QUALITY_OK = "ok";
    public static final String QUALITY_UNCERTAIN = "uncertain";
    public static final String QUALITY_ERROR = "error";
    public static final String QUALITY_DISABLED = "disabled";

    private static final DataSchema STRING_SCHEMA = DataSchema.builder("stringValue")
            .field("value", FieldType.STRING)
            .build();

    private static final DataSchema BOOLEAN_SCHEMA = DataSchema.builder("booleanValue")
            .field("value", FieldType.BOOLEAN)
            .build();

    private static final Set<String> BAD_UPSTREAM_QUALITIES = Set.of(
            QUALITY_UNCERTAIN,
            QUALITY_ERROR,
            QUALITY_DISABLED
    );

    private final ObjectManager objectManager;
    private final BlueprintRegistry blueprintRegistry;
    private final BlueprintApplicationService blueprintApplicationService;
    private final AnalyticsProperties analyticsProperties;
    private final ObjectMapper objectMapper;

    public AnalyticsTagMetadataService(
            ObjectManager objectManager,
            BlueprintRegistry blueprintRegistry,
            BlueprintApplicationService blueprintApplicationService,
            AnalyticsProperties analyticsProperties,
            ObjectMapper objectMapper
    ) {
        this.objectManager = objectManager;
        this.blueprintRegistry = blueprintRegistry;
        this.blueprintApplicationService = blueprintApplicationService;
        this.analyticsProperties = analyticsProperties;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public void ensureTagMetadata(PlatformObject node) {
        if (node.type() != ObjectType.DEVICE || !isAnalyticsTagDevice(node)) {
            return;
        }
        BlueprintDefinition model = blueprintRegistry.findByName(AnalyticsBlueprintBootstrap.ANALYTICS_TAG_MODEL)
                .orElseThrow();
        if (!node.appliedBlueprintIds().contains(model.id())) {
            blueprintApplicationService.applyBlueprintWithRules(model, node.path(), Map.of());
        }
        syncStaticMetadata(node);
        syncHaystackPointMapping(node);
        objectManager.persistNodeTree(node.path());
    }

    @Transactional
    public void recordEvaluations(List<AnalyticsEvaluationResult> results, Instant observedAt) {
        for (AnalyticsEvaluationResult result : results) {
            objectManager.tree().findByPath(result.tagPath()).ifPresent(node -> {
                ensureTagMetadata(node);
                setString(node.path(), "analyticsLastEvalAt", observedAt.toString());
                setString(node.path(), "analyticsLastEvalStatus", result.status());
                if ("ok".equals(result.status())) {
                    setString(node.path(), "analyticsQuality", QUALITY_OK);
                } else if ("skipped".equals(result.status())) {
                    setString(node.path(), "analyticsQuality", QUALITY_UNCERTAIN);
                } else {
                    setString(node.path(), "analyticsQuality", QUALITY_ERROR);
                }
            });
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
            objectManager.tree().findByPath(tag.tagPath()).ifPresent(node -> {
                ensureTagMetadata(node);
                String quality = readQuality(node);
                if (!tag.enabled()) {
                    quality = QUALITY_DISABLED;
                }
                qualityByPath.put(tag.tagPath(), quality);
            });
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
                setString(tag.tagPath(), "analyticsQuality", quality);
                qualityByPath.put(tag.tagPath(), quality);
            }
        }
    }

    public static boolean isAnalyticsTagDevice(PlatformObject node) {
        return node.getVariable("derivedValue").isPresent() || node.getVariable("oeePct").isPresent();
    }

    public static String buildExpression(String helper, List<AnalyticsSourceRef> sources, String windowBucket) {
        String sourceRef = sources.isEmpty()
                ? "?"
                : sources.stream()
                        .map(source -> source.path() + "." + source.variable())
                        .collect(Collectors.joining(", "));
        return helper + "(" + sourceRef + ", " + windowBucket + ")";
    }

    public static List<String> upstreamPathsFromSources(
            List<AnalyticsTagDefinition> tags,
            List<AnalyticsSourceRef> sources
    ) {
        List<String> upstream = new ArrayList<>();
        for (AnalyticsSourceRef source : sources) {
            boolean tagSource = tags.stream().anyMatch(tag ->
                    tag.tagPath().equals(source.path())
                            && tag.outputVariable().equals(source.variable()));
            if (tagSource) {
                upstream.add(source.path());
            }
        }
        return List.copyOf(upstream);
    }

    private void syncStaticMetadata(PlatformObject node) {
        String helper = AnalyticsTagCatalogService.resolveHelper(node);
        String sourcePath = readString(node, "sourcePath").orElse(node.path());
        String sourceVariable = readString(node, "sourceVariable").orElse("");
        String sourceField = readString(node, "sourceField").orElse("value");
        String windowBucket = readString(node, "windowBucket").orElse("5m");
        List<AnalyticsSourceRef> sources = List.of(new AnalyticsSourceRef(sourcePath, sourceVariable, sourceField));
        setString(node.path(), "analyticsHelper", helper);
        setString(node.path(), "analyticsExpression", buildExpression(helper, sources, windowBucket));
        if (!readBoolean(node, "analyticsTagEnabled").isPresent()) {
            setBoolean(node.path(), "analyticsTagEnabled", true);
        }
        if (readString(node, "analyticsQuality").orElse("").isBlank()) {
            setString(node.path(), "analyticsQuality", QUALITY_OK);
        }
    }

    private void syncHaystackPointMapping(PlatformObject node) {
        if (node.getVariable("driverPointMappingsJson").isEmpty()) {
            return;
        }
        String outputVariable = node.getVariable("oeePct").isPresent() ? "oeePct" : "derivedValue";
        String haystackTags = readString(node, "analyticsHaystackTags").orElse("point,cur,his");
        List<String> tags = List.of(haystackTags.split(",")).stream()
                .map(String::trim)
                .filter(tag -> !tag.isBlank())
                .toList();
        if (tags.isEmpty()) {
            return;
        }
        Map<String, DriverPointMappingParser.Entry> mappings = new LinkedHashMap<>(
                DriverPointMappingParser.parse(readString(node, "driverPointMappingsJson").orElse("{}"), objectMapper)
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
            setString(node.path(), "driverPointMappingsJson", objectMapper.writeValueAsString(serialized));
        } catch (Exception ex) {
            throw new IllegalStateException("Failed to serialize Haystack point mappings for " + node.path(), ex);
        }
    }

    public static String readQuality(PlatformObject node) {
        if (!readBoolean(node, "analyticsTagEnabled").orElse(true)) {
            return QUALITY_DISABLED;
        }
        return readString(node, "analyticsQuality").orElse(QUALITY_OK).toLowerCase(Locale.ROOT);
    }

    private void setString(String path, String variable, String value) {
        objectManager.setVariableValue(
                path,
                variable,
                DataRecord.single(STRING_SCHEMA, Map.of("value", value != null ? value : ""))
        );
    }

    private void setBoolean(String path, String variable, boolean value) {
        objectManager.setVariableValue(
                path,
                variable,
                DataRecord.single(BOOLEAN_SCHEMA, Map.of("value", value))
        );
    }

    private static Optional<String> readString(PlatformObject node, String name) {
        return node.getVariable(name).flatMap(Variable::value).map(r -> String.valueOf(r.firstRow().get("value")));
    }

    private static Optional<Boolean> readBoolean(PlatformObject node, String name) {
        return node.getVariable(name).flatMap(Variable::value).map(r -> {
            Object raw = r.firstRow().get("value");
            if (raw instanceof Boolean bool) {
                return bool;
            }
            return Boolean.parseBoolean(String.valueOf(raw));
        });
    }
}
