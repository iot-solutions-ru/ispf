package com.ispf.server.platform.analytics.engine;

import com.ispf.analytics.engine.AnalyticsSourceRef;
import com.ispf.analytics.engine.AnalyticsTagDefinition;
import com.ispf.core.object.ObjectType;
import com.ispf.core.object.PlatformObject;
import com.ispf.core.object.Variable;
import com.ispf.server.config.AnalyticsProperties;
import com.ispf.server.history.HistorianRollupBuckets;
import com.ispf.server.object.ObjectManager;
import com.ispf.server.platform.analytics.catalog.AnalyticsTagCatalogEntry;
import com.ispf.server.platform.analytics.catalog.AnalyticsTagLineageService;
import com.ispf.server.platform.analytics.catalog.AnalyticsTagMetadataService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

/**
 * Loads analytics tag definitions and catalog metadata from the object tree (BL-203, BL-209).
 */
@Service
public class AnalyticsTagCatalogService {

    private final ObjectManager objectManager;
    private final AnalyticsProperties analyticsProperties;
    private final AnalyticsTagLineageService lineageService;
    private final AnalyticsScheduleRegistry scheduleRegistry;

    public AnalyticsTagCatalogService(
            ObjectManager objectManager,
            AnalyticsProperties analyticsProperties,
            AnalyticsTagLineageService lineageService,
            AnalyticsScheduleRegistry scheduleRegistry
    ) {
        this.objectManager = objectManager;
        this.analyticsProperties = analyticsProperties;
        this.lineageService = lineageService;
        this.scheduleRegistry = scheduleRegistry;
    }

    @Transactional(readOnly = true)
    public List<AnalyticsTagDefinition> listEnabledTags() {
        List<AnalyticsTagDefinition> tags = new ArrayList<>();
        for (PlatformObject node : objectManager.tree().all()) {
            if (node.type() != ObjectType.DEVICE) {
                continue;
            }
            toTagDefinition(node).ifPresent(tag -> {
                if (tag.enabled()) {
                    tags.add(tag);
                }
            });
        }
        return tags;
    }

    @Transactional(readOnly = true)
    public List<AnalyticsTagDefinition> listAllTagDefinitions() {
        List<AnalyticsTagDefinition> tags = new ArrayList<>();
        for (PlatformObject node : objectManager.tree().all()) {
            if (node.type() != ObjectType.DEVICE) {
                continue;
            }
            toTagDefinition(node).ifPresent(tags::add);
        }
        return tags;
    }

    @Transactional(readOnly = true)
    public List<AnalyticsTagCatalogEntry> listCatalogEntries(String pathPrefix) {
        List<AnalyticsTagDefinition> definitions = listAllTagDefinitions();
        List<AnalyticsTagCatalogEntry> entries = new ArrayList<>();
        for (PlatformObject node : objectManager.tree().all()) {
            if (node.type() != ObjectType.DEVICE || !AnalyticsTagMetadataService.isAnalyticsTagDevice(node)) {
                continue;
            }
            if (pathPrefix != null && !pathPrefix.isBlank() && !node.path().startsWith(pathPrefix)) {
                continue;
            }
            entries.add(toCatalogEntry(node, definitions));
        }
        return entries;
    }

    @Transactional(readOnly = true)
    public AnalyticsTagCatalogEntry getCatalogEntry(String path) {
        PlatformObject node = objectManager.require(path);
        if (!AnalyticsTagMetadataService.isAnalyticsTagDevice(node)) {
            throw new IllegalArgumentException("Not an analytics tag device: " + path);
        }
        return toCatalogEntry(node, listAllTagDefinitions());
    }

    @Transactional(readOnly = true)
    public List<AnalyticsTagDefinition> tagsAffectedBySource(String objectPath, String variableName) {
        return listEnabledTags().stream()
                .filter(tag -> tag.onChangeEnabled())
                .filter(tag -> tag.sources().stream().anyMatch(source ->
                        source.path().equals(objectPath) && source.variable().equals(variableName)))
                .toList();
    }

    Optional<AnalyticsTagDefinition> toTagDefinition(PlatformObject node) {
        if (!AnalyticsTagMetadataService.isAnalyticsTagDevice(node)) {
            return Optional.empty();
        }
        if (node.getVariable("oeePct").isPresent()) {
            return toOeeTagDefinition(node);
        }
        String helper = resolveHelper(node);
        if (isCelHelper(helper)) {
            return toCelTagDefinition(node, helper);
        }
        String sourcePath = readString(node, "sourcePath").filter(s -> !s.isBlank()).orElse(node.path());
        String sourceVariable = readString(node, "sourceVariable").orElse("");
        if (sourceVariable.isBlank()) {
            return Optional.empty();
        }
        String sourceField = readString(node, "sourceField").orElse("value");
        String windowBucket = readString(node, "windowBucket").orElse("5m");
        List<String> rollupBuckets = readString(node, "rollupBuckets")
                .map(HistorianRollupBuckets::parse)
                .orElse(HistorianRollupBuckets.defaultForWindow(windowBucket));
        boolean enabled = readBoolean(node, "analyticsTagEnabled").orElse(true);
        return Optional.of(new AnalyticsTagDefinition(
                node.path(),
                helper,
                List.of(new AnalyticsSourceRef(sourcePath, sourceVariable, sourceField)),
                windowBucket,
                rollupBuckets,
                analyticsProperties.enginePeriodicMs(),
                true,
                enabled,
                AnalyticsTagDefinition.DEFAULT_OUTPUT
        ));
    }

    private Optional<AnalyticsTagDefinition> toCelTagDefinition(PlatformObject node, String helper) {
        String expression = readString(node, "analyticsExpression").orElse("").trim();
        if (expression.isBlank()) {
            return Optional.empty();
        }
        String windowBucket = readString(node, "windowBucket").orElse("5m");
        List<String> rollupBuckets = readString(node, "rollupBuckets")
                .map(HistorianRollupBuckets::parse)
                .orElse(HistorianRollupBuckets.defaultForWindow(windowBucket));
        boolean enabled = readBoolean(node, "analyticsTagEnabled").orElse(true);
        List<AnalyticsSourceRef> sources = HistorianCelPreprocessor.extractSources(expression);
        return Optional.of(new AnalyticsTagDefinition(
                node.path(),
                helper,
                sources,
                windowBucket,
                rollupBuckets,
                analyticsProperties.enginePeriodicMs(),
                true,
                enabled,
                AnalyticsTagDefinition.DEFAULT_OUTPUT,
                expression
        ));
    }

    private static boolean isCelHelper(String helper) {
        return "cel".equalsIgnoreCase(helper) || "expression".equalsIgnoreCase(helper);
    }

    private Optional<AnalyticsTagDefinition> toOeeTagDefinition(PlatformObject node) {
        String sourcePath = readString(node, "sourcePath").filter(s -> !s.isBlank()).orElse(node.path());
        String availabilityVariable = readString(node, "availabilityVariable").orElse("");
        String performanceVariable = readString(node, "performanceVariable").orElse("");
        String qualityVariable = readString(node, "qualityVariable").orElse("");
        if (availabilityVariable.isBlank() || performanceVariable.isBlank() || qualityVariable.isBlank()) {
            return Optional.empty();
        }
        String sourceField = readString(node, "sourceField").orElse("value");
        String windowBucket = readString(node, "windowBucket").orElse("8h");
        boolean enabled = readBoolean(node, "analyticsTagEnabled").orElse(true);
        List<AnalyticsSourceRef> sources = List.of(
                new AnalyticsSourceRef(sourcePath, availabilityVariable, sourceField),
                new AnalyticsSourceRef(sourcePath, performanceVariable, sourceField),
                new AnalyticsSourceRef(sourcePath, qualityVariable, sourceField)
        );
        return Optional.of(new AnalyticsTagDefinition(
                node.path(),
                "oee",
                sources,
                windowBucket,
                HistorianRollupBuckets.defaultForWindow(windowBucket),
                analyticsProperties.enginePeriodicMs(),
                false,
                enabled,
                "oeePct"
        ));
    }

    private AnalyticsTagCatalogEntry toCatalogEntry(PlatformObject node, List<AnalyticsTagDefinition> definitions) {
        AnalyticsTagDefinition definition = toTagDefinition(node).orElseThrow();
        String expression = readString(node, "analyticsExpression")
                .filter(value -> !value.isBlank())
                .orElse(AnalyticsTagMetadataService.buildExpression(
                        definition.helper(),
                        definition.sources(),
                        definition.windowBucket()
                ));
        List<String> upstream = AnalyticsTagMetadataService.upstreamPathsFromSources(definitions, definition.sources());
        List<String> downstream = lineageService.downstreamTagPaths(definitions, node.path());
        return new AnalyticsTagCatalogEntry(
                node.path(),
                node.displayName(),
                definition.helper(),
                expression,
                definition.outputVariable(),
                definition.sources(),
                upstream,
                downstream,
                definition.windowBucket(),
                definition.rollupBuckets(),
                definition.periodicMs(),
                definition.enabled(),
                AnalyticsTagMetadataService.readQuality(node),
                readString(node, "analyticsLastEvalStatus").orElse(""),
                parseInstant(readString(node, "analyticsLastEvalAt").orElse("")),
                scheduleRegistry.lastTickAt(node.path()),
                lineageService.lineageForTag(definitions, node.path())
        );
    }

    public static String resolveHelper(PlatformObject node) {
        String fromMetadata = readStringStatic(node, "analyticsHelper").orElse("");
        if (!fromMetadata.isBlank()) {
            return fromMetadata;
        }
        for (String blueprintId : node.appliedBlueprintIds()) {
            String lower = blueprintId.toLowerCase(Locale.ROOT);
            if (lower.contains("rolling-avg")) {
                return "rollingAvg";
            }
            if (lower.contains("rate-of-change")) {
                return "rateOfChange";
            }
            if (lower.contains("oee")) {
                return "oee";
            }
            if (lower.contains("analytics-expression") || lower.contains("cel-expression")) {
                return "cel";
            }
        }
        return "rollingAvg";
    }

    private static Optional<String> readString(PlatformObject node, String name) {
        return readStringStatic(node, name);
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

    private static Optional<String> readStringStatic(PlatformObject node, String name) {
        return node.getVariable(name).flatMap(Variable::value).map(r -> String.valueOf(r.firstRow().get("value")));
    }

    private static Instant parseInstant(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        try {
            return Instant.parse(raw);
        } catch (Exception ignored) {
            return null;
        }
    }
}
