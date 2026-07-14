package com.ispf.server.platform.analytics.engine;

import com.ispf.analytics.engine.AnalyticsDagBuilder;
import com.ispf.analytics.engine.AnalyticsTagDefinition;
import com.ispf.analytics.engine.HistorianTagPaths;
import com.ispf.core.binding.BindingRulesConstants;
import com.ispf.core.object.PlatformObject;
import com.ispf.server.config.AnalyticsProperties;
import com.ispf.server.object.BindingRulesService;
import com.ispf.server.object.ObjectManager;
import com.ispf.server.platform.analytics.catalog.AnalyticsTagCatalogEntry;
import com.ispf.server.platform.analytics.catalog.AnalyticsTagLineageService;
import com.ispf.server.platform.analytics.catalog.AnalyticsTagMetadataService;
import com.ispf.server.platform.analytics.catalog.HistorianRuleMetaService;
import com.ispf.server.platform.analytics.pack.AnalyticsExtensionRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
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

    private static final Logger log = LoggerFactory.getLogger(AnalyticsTagCatalogService.class);

    private final ObjectManager objectManager;
    private final BindingRulesService bindingRulesService;
    private final AnalyticsProperties analyticsProperties;
    private final AnalyticsTagLineageService lineageService;
    private final AnalyticsScheduleRegistry scheduleRegistry;
    private final HistorianRuleMetaService historianRuleMetaService;
    private final AnalyticsExtensionRegistry extensionRegistry;
    private final JdbcTemplate jdbcTemplate;

    private final Object catalogLock = new Object();
    private volatile List<AnalyticsTagDefinition> cachedAllTags = List.of();
    private volatile boolean catalogCacheLoaded;

    public AnalyticsTagCatalogService(
            ObjectManager objectManager,
            BindingRulesService bindingRulesService,
            AnalyticsProperties analyticsProperties,
            AnalyticsTagLineageService lineageService,
            AnalyticsScheduleRegistry scheduleRegistry,
            HistorianRuleMetaService historianRuleMetaService,
            AnalyticsExtensionRegistry extensionRegistry,
            JdbcTemplate jdbcTemplate
    ) {
        this.objectManager = objectManager;
        this.bindingRulesService = bindingRulesService;
        this.analyticsProperties = analyticsProperties;
        this.lineageService = lineageService;
        this.scheduleRegistry = scheduleRegistry;
        this.historianRuleMetaService = historianRuleMetaService;
        this.extensionRegistry = extensionRegistry;
        this.jdbcTemplate = jdbcTemplate;
    }

    /** Drops compiled catalog snapshot after binding-rule or historian config changes. */
    public void invalidateCatalog() {
        synchronized (catalogLock) {
            catalogCacheLoaded = false;
            cachedAllTags = List.of();
        }
    }

    @Transactional(readOnly = true)
    public List<AnalyticsTagDefinition> listEnabledTags() {
        return listAllTagDefinitions().stream().filter(AnalyticsTagDefinition::enabled).toList();
    }

    @Transactional(readOnly = true)
    public List<AnalyticsTagDefinition> listAllTagDefinitions() {
        if (catalogCacheLoaded && !cachedAllTags.isEmpty()) {
            return cachedAllTags;
        }
        synchronized (catalogLock) {
            if (catalogCacheLoaded && !cachedAllTags.isEmpty()) {
                return cachedAllTags;
            }
            List<AnalyticsTagDefinition> built = List.copyOf(buildAllTagDefinitions());
            // Never permanently cache an empty catalog — boot/deploy races can see no @bindingRules yet.
            if (!built.isEmpty()) {
                cachedAllTags = built;
                catalogCacheLoaded = true;
            } else {
                cachedAllTags = List.of();
                catalogCacheLoaded = false;
            }
            return built;
        }
    }

    private List<AnalyticsTagDefinition> buildAllTagDefinitions() {
        List<AnalyticsTagDefinition> tags = new ArrayList<>();
        Set<String> extensionIds = extensionHelperIds();
        for (String objectPath : objectPathsWithBindingRules()) {
            try {
                tags.addAll(listTagDefinitionsForObject(objectPath, extensionIds));
            } catch (RuntimeException ex) {
                // Orphan @bindingRules rows or mid-deploy tree gaps must not wipe the whole catalog.
                log.warn("Skipping historian catalog for {}: {}", objectPath, ex.getMessage());
            }
        }
        return tags;
    }

    private List<String> objectPathsWithBindingRules() {
        return jdbcTemplate.queryForList(
                """
                        SELECT DISTINCT object_path
                        FROM object_variables
                        WHERE name = ?
                        """,
                String.class,
                BindingRulesConstants.RULES_VARIABLE
        );
    }

    @Transactional(readOnly = true)
    public List<AnalyticsTagDefinition> listTagDefinitionsForObject(String objectPath) {
        return listTagDefinitionsForObject(objectPath, extensionHelperIds());
    }

    private List<AnalyticsTagDefinition> listTagDefinitionsForObject(
            String objectPath,
            Set<String> extensionIds
    ) {
        return HistorianBindingRuleCompiler.compileAll(
                objectPath,
                bindingRulesService.listRules(objectPath),
                analyticsProperties,
                extensionIds
        );
    }

    private Set<String> extensionHelperIds() {
        return extensionRegistry.registeredFunctions().stream()
                .map(AnalyticsExtensionRegistry.RegisteredAnalyticsFunction::helperId)
                .collect(Collectors.toUnmodifiableSet());
    }

    @Transactional(readOnly = true)
    public List<AnalyticsTagCatalogEntry> listCatalogEntries(String pathPrefix) {
        List<AnalyticsTagDefinition> definitions = listAllTagDefinitions();
        return definitions.stream()
                .filter(definition -> pathPrefix == null || pathPrefix.isBlank()
                        || definition.objectPath().startsWith(pathPrefix))
                .map(definition -> toCatalogEntry(definition, definitions))
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
    public Optional<AnalyticsTagDefinition> findTagDefinition(String tagPath) {
        if (tagPath == null || tagPath.isBlank()) {
            return Optional.empty();
        }
        if (HistorianTagPaths.isComposite(tagPath)) {
            String objectPath = HistorianTagPaths.objectPath(tagPath);
            String ruleId = HistorianTagPaths.ruleId(tagPath);
            return listTagDefinitionsForObject(objectPath).stream()
                    .filter(definition -> definition.tagPath().equals(tagPath)
                            || definition.ruleId().equals(ruleId))
                    .findFirst();
        }
        return listTagDefinitionsForObject(tagPath).stream().findFirst();
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
