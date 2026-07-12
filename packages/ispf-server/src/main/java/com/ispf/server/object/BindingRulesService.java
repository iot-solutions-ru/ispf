package com.ispf.server.object;

import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;
import com.ispf.analytics.engine.AnalyticsTagDefinition;
import com.ispf.core.binding.BindingActivators;
import com.ispf.core.binding.BindingRule;
import com.ispf.core.binding.BindingRuleKind;
import com.ispf.core.binding.BindingRulesConstants;
import com.ispf.core.binding.BindingTarget;
import com.ispf.core.binding.BindingVariableRef;
import com.ispf.core.model.DataRecord;
import com.ispf.core.model.DataSchema;
import com.ispf.core.model.FieldType;
import com.ispf.core.object.ObjectType;
import com.ispf.core.object.PlatformObject;
import com.ispf.core.object.Variable;
import com.ispf.expression.BindingDependencyParser;
import com.ispf.server.config.AnalyticsProperties;
import com.ispf.server.history.HistorianRollupBuckets;
import com.ispf.server.history.VariableHistoryService;
import com.ispf.server.platform.analytics.engine.AnalyticsEngineScheduler;
import com.ispf.server.platform.analytics.engine.AnalyticsTagCatalogService;
import com.ispf.server.platform.analytics.engine.HistorianBindingRuleCompiler;
import com.ispf.server.platform.analytics.formula.BindingFormulaResolver;
import com.ispf.core.ref.PlatformRef;
import com.ispf.core.ref.PlatformRefKind;
import com.ispf.core.ref.PlatformRefParser;
import com.ispf.server.ref.PlatformRefResolver;
import com.ispf.server.platform.analytics.pack.AnalyticsExtensionRegistry;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

@Service
public class BindingRulesService {

    private static final DataSchema RULES_SCHEMA = DataSchema.builder("bindingRulesJson")
            .field("value", FieldType.STRING)
            .build();

    private static final DataSchema STRING_VALUE_SCHEMA = DataSchema.builder("stringValue")
            .field("value", FieldType.STRING)
            .build();

    private final ObjectManager objectManager;
    private final ObjectMapper objectMapper;
    private final BindingPeriodicScheduleRegistry periodicScheduleRegistry;
    private final BindingPeriodicScheduler periodicScheduler;
    private final AnalyticsProperties analyticsProperties;
    private final AnalyticsEngineScheduler engineScheduler;
    private final AnalyticsExtensionRegistry extensionRegistry;
    private final BindingFormulaResolver bindingFormulaResolver;
    private final AnalyticsTagCatalogService tagCatalogService;
    private final ConcurrentHashMap<String, Object> rulesLocks = new ConcurrentHashMap<>();

    public BindingRulesService(
            ObjectManager objectManager,
            ObjectMapper objectMapper,
            BindingPeriodicScheduleRegistry periodicScheduleRegistry,
            @Lazy BindingPeriodicScheduler periodicScheduler,
            AnalyticsProperties analyticsProperties,
            @Lazy AnalyticsEngineScheduler engineScheduler,
            AnalyticsExtensionRegistry extensionRegistry,
            BindingFormulaResolver bindingFormulaResolver,
            @Lazy AnalyticsTagCatalogService tagCatalogService
    ) {
        this.objectManager = objectManager;
        this.objectMapper = objectMapper;
        this.periodicScheduleRegistry = periodicScheduleRegistry;
        this.periodicScheduler = periodicScheduler;
        this.analyticsProperties = analyticsProperties;
        this.engineScheduler = engineScheduler;
        this.extensionRegistry = extensionRegistry;
        this.bindingFormulaResolver = bindingFormulaResolver;
        this.tagCatalogService = tagCatalogService;
    }

    public List<BindingRule> listRules(String objectPath) {
        PlatformObject object = objectManager.require(objectPath);
        return readRules(object);
    }

    public List<BindingRule> saveRules(String objectPath, List<BindingRule> rules) {
        synchronized (rulesLocks.computeIfAbsent(objectPath, ignored -> new Object())) {
            List<BindingRule> normalized = normalizeRules(rules).stream()
                    .map(bindingFormulaResolver::resolve)
                    .toList();
            for (BindingRule rule : normalized) {
                validateRule(objectPath, rule);
            }
            boolean historianChanged = normalized.stream().anyMatch(BindingRule::isHistorian);
            writeRules(objectPath, normalized);
            periodicScheduleRegistry.syncObject(
                    objectPath,
                    normalized.stream().filter(BindingRule::isReactive).toList()
            );
            periodicScheduler.reschedule();
            if (historianChanged) {
                engineScheduler.syncSchedules();
            }
            tagCatalogService.invalidateCatalog();
            return normalized;
        }
    }

    public BindingRule upsertRule(String objectPath, BindingRule rule) {
        validateRule(objectPath, rule);
        List<BindingRule> rules = new ArrayList<>(listRules(objectPath));
        rules.removeIf(existing -> existing.id().equals(rule.id()));
        rules.add(rule);
        return saveRules(objectPath, rules).stream()
                .filter(r -> r.id().equals(rule.id()))
                .findFirst()
                .orElseThrow();
    }

    public void deleteRule(String objectPath, String ruleId) {
        List<BindingRule> rules = listRules(objectPath).stream()
                .filter(rule -> !rule.id().equals(ruleId))
                .toList();
        saveRules(objectPath, rules);
    }

    public static BindingActivators defaultActivators(String objectPath, String expression) {
        var remoteRefs = BindingDependencyParser.parseRefAtDependencies(expression);
        if (!remoteRefs.isEmpty()) {
            return new BindingActivators(false, new ArrayList<>(remoteRefs), null, 0);
        }
        return BindingActivators.onLocalChange();
    }

    public static List<BindingVariableRef> refsFromActivators(List<BindingVariableRef> activators) {
        if (activators == null) {
            return List.of();
        }
        return activators.stream()
                .map(BindingVariableRef::normalize)
                .toList();
    }

    private void validateRule(String objectPath, BindingRule rule) {
        if (rule.isHistorian()) {
            validateHistorianRule(objectPath, rule);
            return;
        }
        PlatformObject object = objectManager.require(objectPath);
        BindingTarget target = rule.target();
        if (target.isVariable()) {
            if (target.ref() != null && !target.ref().isBlank()) {
                validateVariableTargetRef(objectPath, target.ref());
                return;
            }
            if (target.variableName() == null || target.variableName().isBlank()) {
                throw new IllegalArgumentException("Variable target.variableName or target.ref is required");
            }
            if (object.getVariable(target.variableName()).isEmpty()) {
                throw new IllegalArgumentException("Unknown target variable: " + target.variableName());
            }
            return;
        }
        if (target.isContext()) {
            if (object.type() != ObjectType.DASHBOARD) {
                throw new IllegalArgumentException("Context target is only allowed on DASHBOARD objects");
            }
            if (target.path() == null || target.path().isBlank()) {
                throw new IllegalArgumentException("Context target.path is required");
            }
            return;
        }
        if (target.isEvent()) {
            if (target.ref() != null && !target.ref().isBlank()) {
                validateEventTargetRef(objectPath, target.ref());
                return;
            }
            if (target.eventName() == null || target.eventName().isBlank()) {
                throw new IllegalArgumentException("Event target.eventName or target.ref is required");
            }
            if (object.events().get(target.eventName()) == null) {
                throw new IllegalArgumentException("Unknown event: " + target.eventName());
            }
            return;
        }
        if (target.isAction()) {
            return;
        }
    }

    private void validateVariableTargetRef(String ruleObjectPath, String refRaw) {
        PlatformRef ref = PlatformRefResolver.resolve(PlatformRefParser.parseVariableSource(refRaw), ruleObjectPath);
        PlatformObject targetObject = objectManager.require(ref.object());
        if (targetObject.getVariable(ref.name()).isEmpty()) {
            throw new IllegalArgumentException("Unknown target variable ref: " + refRaw);
        }
    }

    private void validateEventTargetRef(String ruleObjectPath, String refRaw) {
        PlatformRef ref = PlatformRefResolver.resolve(PlatformRefParser.parse(refRaw), ruleObjectPath);
        if (ref.kind() != PlatformRefKind.EVENT) {
            throw new IllegalArgumentException("Event target ref must use /evt/ grammar: " + refRaw);
        }
        PlatformObject targetObject = objectManager.require(ref.object());
        if (targetObject.events().get(ref.name()) == null) {
            throw new IllegalArgumentException("Unknown target event ref: " + refRaw);
        }
    }

    private void validateHistorianRule(String objectPath, BindingRule rule) {
        if (!rule.target().isVariable()) {
            throw new IllegalArgumentException("Historian rules must target a variable");
        }
        String windowBucket = rule.windowBucket() != null && !rule.windowBucket().isBlank()
                ? rule.windowBucket()
                : "5m";
        validateWindowBucket(windowBucket);
        if (rule.rollupBuckets() != null) {
            for (String bucket : rule.rollupBuckets()) {
                validateWindowBucket(bucket);
            }
        }
        AnalyticsTagDefinition compiled = HistorianBindingRuleCompiler.compile(
                        objectPath,
                        rule,
                        analyticsProperties,
                        extensionHelperIds())
                .orElseThrow(() -> new IllegalArgumentException(
                        "Historian rule does not compile: " + rule.id()));
        ensureHistorianTargetVariable(objectPath, rule, compiled);
    }

    private static void validateWindowBucket(String windowBucket) {
        VariableHistoryService.parseBucket(windowBucket);
    }

    private void ensureHistorianTargetVariable(
            String objectPath,
            BindingRule rule,
            AnalyticsTagDefinition compiled
    ) {
        String variableName = rule.target().variableName();
        PlatformObject object = objectManager.require(objectPath);
        if (object.getVariable(variableName).isPresent()) {
            return;
        }
        objectManager.createVariable(
                objectPath,
                variableName,
                STRING_VALUE_SCHEMA,
                true,
                true,
                DataRecord.single(STRING_VALUE_SCHEMA, Map.of("value", "")),
                false,
                null
        );
    }

    private Set<String> extensionHelperIds() {
        return extensionRegistry.registeredFunctions().stream()
                .map(AnalyticsExtensionRegistry.RegisteredAnalyticsFunction::helperId)
                .collect(Collectors.toUnmodifiableSet());
    }

    private List<BindingRule> readRules(PlatformObject object) {
        return object.getVariable(BindingRulesConstants.RULES_VARIABLE)
                .flatMap(Variable::value)
                .map(record -> record.firstRow().get("value"))
                .map(Object::toString)
                .filter(json -> !json.isBlank())
                .map(this::parseRules)
                .orElse(List.of());
    }

    private List<BindingRule> parseRules(String json) {
        try {
            List<BindingRuleDto> dtos = objectMapper.readValue(json, new TypeReference<>() {});
            return dtos.stream().map(BindingRuleDto::toRule).toList();
        } catch (Exception e) {
            throw new IllegalArgumentException("Invalid binding rules JSON: " + e.getMessage());
        }
    }

    private void writeRules(String objectPath, List<BindingRule> rules) {
        try {
            List<BindingRuleDto> dtos = rules.stream().map(BindingRuleDto::from).toList();
            String json = objectMapper.writeValueAsString(dtos);
            DataRecord record = DataRecord.single(RULES_SCHEMA, Map.of("value", json));
            objectManager.upsertSystemVariable(objectPath, BindingRulesConstants.RULES_VARIABLE, RULES_SCHEMA, record);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to persist binding rules", e);
        }
    }

    private List<BindingRule> normalizeRules(List<BindingRule> rules) {
        List<BindingRule> normalized = new ArrayList<>();
        for (BindingRule rule : rules) {
            BindingActivators activators = normalizeActivators(rule.activators(), rule.expression());
            BindingRuleKind kind = rule.kind() != null ? rule.kind() : BindingRuleKind.REACTIVE;
            String windowBucket = blankToNull(rule.windowBucket());
            List<String> rollupBuckets = normalizeRollupBuckets(rule.rollupBuckets(), windowBucket);
            normalized.add(new BindingRule(
                    rule.id(),
                    rule.name(),
                    rule.enabled(),
                    rule.order(),
                    kind,
                    activators,
                    rule.condition(),
                    rule.expression(),
                    rule.target(),
                    windowBucket,
                    rollupBuckets,
                    rule.formulaRef(),
                    rule.formulaParams(),
                    rule.formulaScope(),
                    rule.formulaAppId()
            ));
        }
        normalized.sort((left, right) -> Integer.compare(left.order(), right.order()));
        return normalized;
    }

    private static List<String> normalizeRollupBuckets(List<String> rollupBuckets, String windowBucket) {
        if (rollupBuckets != null && !rollupBuckets.isEmpty()) {
            return List.copyOf(rollupBuckets);
        }
        if (windowBucket == null || windowBucket.isBlank()) {
            return null;
        }
        return HistorianRollupBuckets.defaultForWindow(windowBucket);
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value;
    }

    private static BindingActivators normalizeActivators(BindingActivators activators, String expression) {
        String onEvent = activators.onEvent();
        if (onEvent != null) {
            onEvent = onEvent.trim();
            if (onEvent.isBlank()) {
                onEvent = null;
            }
        }
        long periodicMs = Math.max(0L, activators.periodicMs());
        if (onEvent != activators.onEvent() || periodicMs != activators.periodicMs()) {
            activators = new BindingActivators(
                    activators.onStartup(),
                    activators.onVariableChange(),
                    onEvent,
                    periodicMs,
                    activators.async(),
                    activators.onContextChange()
            );
        }
        if (activators.onVariableChange().isEmpty()
                && !activators.onStartup()
                && !activators.onContextChange()
                && !activators.hasPeriodicSchedule()
                && (activators.onEvent() == null || activators.onEvent().isBlank())) {
            activators = defaultActivators("", expression);
        }
        return activators;
    }

    private record BindingRuleDto(
            String id,
            String name,
            boolean enabled,
            int order,
            String kind,
            BindingActivatorsDto activators,
            String condition,
            String expression,
            BindingTargetDto target,
            String windowBucket,
            List<String> rollupBuckets,
            String formulaRef,
            Map<String, String> formulaParams,
            String formulaScope,
            String formulaAppId
    ) {
        static BindingRuleDto from(BindingRule rule) {
            return new BindingRuleDto(
                    rule.id(),
                    rule.name(),
                    rule.enabled(),
                    rule.order(),
                    rule.kind().name().toLowerCase(Locale.ROOT),
                    BindingActivatorsDto.from(rule.activators()),
                    rule.condition(),
                    rule.expression(),
                    new BindingTargetDto(
                            rule.target().kind(),
                            rule.target().variableName(),
                            rule.target().field(),
                            rule.target().path(),
                            rule.target().eventName(),
                            rule.target().ref()
                    ),
                    rule.windowBucket(),
                    rule.rollupBuckets(),
                    rule.formulaRef(),
                    rule.formulaParams(),
                    rule.formulaScope(),
                    rule.formulaAppId()
            );
        }

        BindingRule toRule() {
            BindingRuleKind parsedKind = BindingRuleKind.REACTIVE;
            if (kind != null && !kind.isBlank()) {
                parsedKind = BindingRuleKind.valueOf(kind.trim().toUpperCase(Locale.ROOT));
            }
            return new BindingRule(
                    id,
                    name,
                    enabled,
                    order,
                    parsedKind,
                    activators != null ? activators.toActivators() : null,
                    condition,
                    expression,
                    new BindingTarget(
                            target.kind(),
                            target.variableName(),
                            target.field(),
                            target.path(),
                            target.eventName(),
                            target.ref()
                    ),
                    windowBucket,
                    rollupBuckets,
                    formulaRef,
                    formulaParams,
                    formulaScope,
                    formulaAppId
            );
        }
    }

    private record BindingActivatorsDto(
            boolean onStartup,
            List<BindingVariableRefDto> onVariableChange,
            String onEvent,
            long periodicMs,
            Boolean async,
            Boolean onContextChange
    ) {
        static BindingActivatorsDto from(BindingActivators activators) {
            return new BindingActivatorsDto(
                    activators.onStartup(),
                    activators.onVariableChange().stream().map(BindingVariableRefDto::from).toList(),
                    activators.onEvent(),
                    activators.periodicMs(),
                    activators.async(),
                    activators.onContextChange()
            );
        }

        BindingActivators toActivators() {
            List<BindingVariableRef> refs = onVariableChange != null
                    ? onVariableChange.stream().map(BindingVariableRefDto::toRef).toList()
                    : List.of();
            return new BindingActivators(
                    onStartup,
                    refs,
                    onEvent,
                    periodicMs,
                    async != null && async,
                    onContextChange != null && onContextChange
            );
        }
    }

    private record BindingVariableRefDto(String objectPath, String variableName) {
        static BindingVariableRefDto from(BindingVariableRef ref) {
            return new BindingVariableRefDto(ref.objectPath(), ref.variableName());
        }

        BindingVariableRef toRef() {
            return new BindingVariableRef(objectPath, variableName);
        }
    }

    private record BindingTargetDto(
            String kind,
            String variableName,
            String field,
            String path,
            String eventName,
            String ref
    ) {
    }
}
