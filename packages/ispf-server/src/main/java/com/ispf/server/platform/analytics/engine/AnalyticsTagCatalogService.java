package com.ispf.server.platform.analytics.engine;

import com.ispf.analytics.engine.AnalyticsDagBuilder;
import com.ispf.analytics.engine.AnalyticsTagDefinition;
import com.ispf.analytics.engine.HistorianTagPaths;
import com.ispf.core.object.ObjectType;
import com.ispf.core.object.PlatformObject;
import com.ispf.server.config.AnalyticsProperties;
import com.ispf.server.object.BindingRulesService;
import com.ispf.server.object.ObjectManager;
import com.ispf.server.platform.analytics.catalog.AnalyticsTagCatalogEntry;
import com.ispf.server.platform.analytics.catalog.AnalyticsTagLineageService;
import com.ispf.server.platform.analytics.catalog.AnalyticsTagMetadataService;
import com.ispf.server.platform.analytics.catalog.HistorianRuleMetaService;
import com.ispf.server.platform.analytics.pack.AnalyticsExtensionRegistry;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Loads analytics tag definitions from historian binding rules (ADR-0041).
 */
@Service
public class AnalyticsTagCatalogService {

    private final ObjectManager objectManager;
    private final BindingRulesService bindingRulesService;
    private final AnalyticsProperties analyticsProperties;
    private final AnalyticsTagLineageService lineageService;
    private final AnalyticsScheduleRegistry scheduleRegistry;
    private final HistorianRuleMetaService historianRuleMetaService;
    private final AnalyticsExtensionRegistry extensionRegistry;

    public AnalyticsTagCatalogService(
            ObjectManager objectManager,
            BindingRulesService bindingRulesService,
            AnalyticsProperties analyticsProperties,
            AnalyticsTagLineageService lineageService,
            AnalyticsScheduleRegistry scheduleRegistry,
            HistorianRuleMetaService historianRuleMetaService,
            AnalyticsExtensionRegistry extensionRegistry
    ) {
        this.objectManager = objectManager;
        this.bindingRulesService = bindingRulesService;
        this.analyticsProperties = analyticsProperties;
        this.lineageService = lineageService;
        this.scheduleRegistry = scheduleRegistry;
        this.historianRuleMetaService = historianRuleMetaService;
        this.extensionRegistry = extensionRegistry;
    }

    @Transactional(readOnly = true)
    public List<AnalyticsTagDefinition> listEnabledTags() {
        return listAllTagDefinitions().stream().filter(AnalyticsTagDefinition::enabled).toList();
    }

    @Transactional(readOnly = true)
    public List<AnalyticsTagDefinition> listAllTagDefinitions() {
        List<AnalyticsTagDefinition> tags = new ArrayList<>();
        for (PlatformObject node : objectManager.tree().all()) {
            if (node.type() != ObjectType.DEVICE) {
                continue;
            }
            tags.addAll(listTagDefinitionsForObject(node.path()));
        }
        return tags;
    }

    @Transactional(readOnly = true)
    public List<AnalyticsTagDefinition> listTagDefinitionsForObject(String objectPath) {
        return HistorianBindingRuleCompiler.compileAll(
                objectPath,
                bindingRulesService.listRules(objectPath),
                analyticsProperties,
                extensionHelperIds()
        );
    }

    private Set<String> extensionHelperIds() {
        return extensionRegistry.registeredFunctions().stream()
                .map(AnalyticsExtensionRegistry.RegisteredAnalyticsFunction::helperId)
                .collect(Collectors.toUnmodifiableSet());
    }

    @Transactional(readOnly = true)
    public List<AnalyticsTagCatalogEntry> listCatalogEntries(String pathPrefix) {
        return listAllTagDefinitions().stream()
                .filter(definition -> pathPrefix == null || pathPrefix.isBlank()
                        || definition.objectPath().startsWith(pathPrefix))
                .map(definition -> toCatalogEntry(definition, listAllTagDefinitions()))
                .toList();
    }

    @Transactional(readOnly = true)
    public List<AnalyticsTagCatalogEntry> listCatalogEntriesForObject(String objectPath) {
        List<AnalyticsTagDefinition> definitions = listAllTagDefinitions();
        return listTagDefinitionsForObject(objectPath).stream()
                .map(definition -> toCatalogEntry(definition, definitions))
                .toList();
    }

    @Transactional(readOnly = true)
    public Optional<AnalyticsTagCatalogEntry> findCatalogEntry(String tagPath) {
        if (tagPath == null || tagPath.isBlank()) {
            return Optional.empty();
        }
        if (HistorianTagPaths.isComposite(tagPath)) {
            return findCatalogEntryByTagPath(tagPath);
        }
        return listCatalogEntriesForObject(tagPath).stream().findFirst();
    }

    @Transactional(readOnly = true)
    public Optional<AnalyticsTagCatalogEntry> findCatalogEntryByTagPath(String tagPath) {
        String objectPath = HistorianTagPaths.objectPath(tagPath);
        String ruleId = HistorianTagPaths.ruleId(tagPath);
        return listTagDefinitionsForObject(objectPath).stream()
                .filter(definition -> definition.tagPath().equals(tagPath)
                        || definition.ruleId().equals(ruleId))
                .findFirst()
                .map(definition -> toCatalogEntry(definition, listAllTagDefinitions()));
    }

    @Transactional(readOnly = true)
    public AnalyticsTagCatalogEntry getCatalogEntry(String tagPath) {
        return findCatalogEntry(tagPath).orElseThrow(() ->
                new IllegalArgumentException("Historian computation not found: " + tagPath));
    }

    @Transactional(readOnly = true)
    public List<AnalyticsTagDefinition> tagsAffectedBySource(String objectPath, String variableName) {
        return listEnabledTags().stream()
                .filter(AnalyticsTagDefinition::onChangeEnabled)
                .filter(tag -> tag.sources().stream().anyMatch(source ->
                        source.path().equals(objectPath) && source.variable().equals(variableName)))
                .toList();
    }

    private AnalyticsTagCatalogEntry toCatalogEntry(
            AnalyticsTagDefinition definition,
            List<AnalyticsTagDefinition> definitions
    ) {
        PlatformObject node = objectManager.require(definition.objectPath());
        String expression = definition.expressionOrEmpty();
        if (expression.isBlank() && !definition.isCelHelper()) {
            expression = AnalyticsTagMetadataService.buildExpression(
                    definition.helper(),
                    definition.sources(),
                    definition.windowBucket()
            );
        }
        HistorianRuleMetaService.RuleMeta meta = historianRuleMetaService.readRuleMeta(node, definition.ruleId());
        String quality = resolveCatalogQuality(definition, definitions, meta.quality());
        String displayName = node.displayName();
        if (!definition.ruleId().isBlank()) {
            displayName = displayName + " / " + definition.ruleId();
        }
        return new AnalyticsTagCatalogEntry(
                definition.tagPath(),
                displayName,
                definition.helper(),
                expression,
                definition.outputVariable(),
                definition.sources(),
                AnalyticsTagMetadataService.upstreamTagPathsFromSources(definitions, definition.sources()),
                lineageService.downstreamTagPaths(definitions, definition.tagPath()),
                definition.windowBucket(),
                definition.rollupBuckets(),
                definition.periodicMs(),
                definition.enabled(),
                quality,
                meta.lastEvalStatus(),
                meta.lastEvalAt(),
                scheduleRegistry.lastTickAt(definition.tagPath()),
                lineageService.lineageForTag(definitions, definition.tagPath())
        );
    }

    private static String resolveCatalogQuality(
            AnalyticsTagDefinition definition,
            List<AnalyticsTagDefinition> definitions,
            String storedQuality
    ) {
        if (!definition.enabled()) {
            return HistorianRuleMetaService.QUALITY_DISABLED;
        }
        String quality = storedQuality != null && !storedQuality.isBlank()
                ? storedQuality
                : HistorianRuleMetaService.QUALITY_OK;
        if (definitions.isEmpty()) {
            return quality;
        }
        AnalyticsDagBuilder.AnalyticsTagAdjacency adjacency = AnalyticsDagBuilder.adjacency(definitions);
        for (String upstream : adjacency.upstreamByTag().getOrDefault(definition.tagPath(), Set.of())) {
            Optional<AnalyticsTagDefinition> upstreamTag = definitions.stream()
                    .filter(tag -> tag.tagPath().equals(upstream))
                    .findFirst();
            if (upstreamTag.isEmpty()) {
                continue;
            }
            String upstreamQuality = !upstreamTag.get().enabled()
                    ? HistorianRuleMetaService.QUALITY_DISABLED
                    : HistorianRuleMetaService.QUALITY_OK;
            if (HistorianRuleMetaService.QUALITY_UNCERTAIN.equals(upstreamQuality)
                    || HistorianRuleMetaService.QUALITY_ERROR.equals(upstreamQuality)
                    || HistorianRuleMetaService.QUALITY_DISABLED.equals(upstreamQuality)) {
                return HistorianRuleMetaService.QUALITY_UNCERTAIN;
            }
        }
        return quality;
    }
}
