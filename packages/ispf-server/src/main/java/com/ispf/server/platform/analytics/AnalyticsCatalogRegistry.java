package com.ispf.server.platform.analytics;

import com.ispf.analytics.engine.AnalyticsEvaluatorRegistry;
import com.ispf.analytics.spi.AnalyticsFunctionDescriptor;
import com.ispf.server.platform.analytics.catalog.AnalyticsCatalogEntry;
import com.ispf.server.platform.analytics.catalog.AnalyticsCatalogParameter;
import com.ispf.server.platform.analytics.pack.AnalyticsExtensionRegistry;
import com.ispf.server.platform.analytics.formula.AnalyticsFormula;
import com.ispf.server.platform.analytics.formula.AnalyticsFormulaParameter;
import com.ispf.server.platform.analytics.formula.AnalyticsFormulaService;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * In-memory analytics function catalog (Tier A built-ins + Tier C extensions).
 */
@Component
public class AnalyticsCatalogRegistry {

    private final Map<String, AnalyticsCatalogEntry> baseEntriesById;
    private final AnalyticsExtensionRegistry extensionRegistry;
    private final AnalyticsFormulaService formulaService;

    public AnalyticsCatalogRegistry(
            AnalyticsExtensionRegistry extensionRegistry,
            AnalyticsFormulaService formulaService
    ) {
        this.extensionRegistry = extensionRegistry;
        this.formulaService = formulaService;
        this.baseEntriesById = buildBaseCatalog();
    }

    public List<AnalyticsCatalogEntry> list() {
        return List.copyOf(buildEffectiveCatalog().values());
    }

    public Optional<AnalyticsCatalogEntry> findById(String functionId) {
        if (functionId == null || functionId.isBlank()) {
            return Optional.empty();
        }
        return Optional.ofNullable(buildEffectiveCatalog().get(functionId));
    }

    private static Map<String, AnalyticsCatalogEntry> buildBaseCatalog() {
        Map<String, AnalyticsCatalogEntry> catalog = new LinkedHashMap<>();
        registerEvaluatorBuiltins(catalog);
        registerHistorianCelBuiltins(catalog);
        registerPresets(catalog);
        return Collections.unmodifiableMap(new LinkedHashMap<>(catalog));
    }

    private Map<String, AnalyticsCatalogEntry> buildEffectiveCatalog() {
        Map<String, AnalyticsCatalogEntry> catalog = new LinkedHashMap<>(baseEntriesById);
        registerPackFunctions(catalog, extensionRegistry);
        registerUserFormulas(catalog, formulaService.listAllForCatalog());
        return Collections.unmodifiableMap(catalog);
    }

    private static void registerEvaluatorBuiltins(Map<String, AnalyticsCatalogEntry> catalog) {
        for (String helper : AnalyticsEvaluatorRegistry.builtins().helpers()) {
            AnalyticsCatalogEntry entry = switch (helper) {
                case "rollingAvg" -> new AnalyticsCatalogEntry(
                        "rollingAvg",
                        "Rolling average",
                        "A",
                        List.of("helper", "binding-rule"),
                        "rollingAvg(<objectPath.variable>, <windowBucket?>)",
                        List.of(
                                parameter("source", "string", true, "Path + variable in <objectPath.variable> form", null),
                                parameter("windowBucket", "string", false, "Historian bucket (e.g. 5m, 1h)", "5m")
                        ),
                        "Returns the latest historian window average for a source.",
                        List.of("rollingAvg(root.devices.pump01.temperature, 5m)"),
                        List.of("historian", "window", "builtin"),
                        "core",
                        "analytics-catalog-rollingavg"
                );
                case "rateOfChange" -> new AnalyticsCatalogEntry(
                        "rateOfChange",
                        "Rate of change",
                        "A",
                        List.of("helper", "binding-rule"),
                        "rateOfChange(<objectPath.variable>, <windowBucket?>)",
                        List.of(
                                parameter("source", "string", true, "Path + variable in <objectPath.variable> form", null),
                                parameter("windowBucket", "string", false, "Historian bucket (e.g. 5m, 1h)", "5m")
                        ),
                        "Calculates delta between first and last bucket averages in the window.",
                        List.of("rateOfChange(root.devices.pump01.flowRate, 1h)"),
                        List.of("historian", "delta", "builtin"),
                        "core",
                        "analytics-catalog-rateofchange"
                );
                case "totalizer" -> new AnalyticsCatalogEntry(
                        "totalizer",
                        "Totalizer",
                        "A",
                        List.of("helper", "binding-rule"),
                        "totalizer(<objectPath.variable>, <windowBucket?>)",
                        List.of(
                                parameter("source", "string", true, "Path + variable in <objectPath.variable> form", null),
                                parameter("windowBucket", "string", false, "Historian bucket (e.g. 5m, 1h)", "5m")
                        ),
                        "Accumulates bucket averages over a window for counter-like metrics.",
                        List.of("totalizer(root.devices.meter01.energy, 1h)"),
                        List.of("historian", "accumulation", "builtin"),
                        "core",
                        "analytics-catalog-totalizer"
                );
                case "last" -> new AnalyticsCatalogEntry(
                        "last",
                        "Last sample",
                        "A",
                        List.of("helper", "binding-rule"),
                        "last(<objectPath.variable>)",
                        List.of(parameter("source", "string", true, "Path + variable in <objectPath.variable> form", null)),
                        "Reads the most recent sample, with live fallback if historian is empty.",
                        List.of("last(root.devices.pump01.temperature)"),
                        List.of("historian", "latest", "builtin"),
                        "core",
                        "analytics-catalog-last"
                );
                case "min" -> new AnalyticsCatalogEntry(
                        "min",
                        "Minimum in window",
                        "A",
                        List.of("helper", "binding-rule"),
                        "min(<objectPath.variable>, <windowBucket?>)",
                        List.of(
                                parameter("source", "string", true, "Path + variable in <objectPath.variable> form", null),
                                parameter("windowBucket", "string", false, "Historian bucket (e.g. 5m, 1h)", "5m")
                        ),
                        "Returns the minimum value across historian buckets in a window.",
                        List.of("min(root.devices.pump01.pressure, 30m)"),
                        List.of("historian", "extrema", "builtin"),
                        "core",
                        "analytics-catalog-min"
                );
                case "max" -> new AnalyticsCatalogEntry(
                        "max",
                        "Maximum in window",
                        "A",
                        List.of("helper", "binding-rule"),
                        "max(<objectPath.variable>, <windowBucket?>)",
                        List.of(
                                parameter("source", "string", true, "Path + variable in <objectPath.variable> form", null),
                                parameter("windowBucket", "string", false, "Historian bucket (e.g. 5m, 1h)", "5m")
                        ),
                        "Returns the maximum value across historian buckets in a window.",
                        List.of("max(root.devices.pump01.pressure, 30m)"),
                        List.of("historian", "extrema", "builtin"),
                        "core",
                        "analytics-catalog-max"
                );
                default -> new AnalyticsCatalogEntry(
                        helper,
                        helper,
                        "A",
                        List.of("helper", "binding-rule"),
                        helper + "(...)",
                        List.of(),
                        "Built-in analytics helper.",
                        List.of(helper + "(...)"),
                        List.of("builtin"),
                        "core",
                        "analytics-catalog-" + helper.toLowerCase()
                );
            };
            catalog.put(entry.id(), entry);
        }
    }

    private static void registerHistorianCelBuiltins(Map<String, AnalyticsCatalogEntry> catalog) {
        registerIfAbsent(catalog, histAggregate("hist.avg", "Average in window", "avg"));
        registerIfAbsent(catalog, histAggregate("hist.min", "Minimum in window", "min"));
        registerIfAbsent(catalog, histAggregate("hist.max", "Maximum in window", "max"));
        registerIfAbsent(catalog, histAggregate("hist.sum", "Sum of bucket averages", "sum"));
        registerIfAbsent(catalog, histAggregate("hist.last", "Last sample in window", "last"));
        registerIfAbsent(catalog, new AnalyticsCatalogEntry(
                "hist.live",
                "Live variable value",
                "A",
                List.of("cel", "historian"),
                "hist.live('<objectPath>', '<variable>', '<field?>')",
                List.of(
                        parameter("objectPath", "string", true, "Object path to read from", null),
                        parameter("variable", "string", true, "Variable name", null),
                        parameter("field", "string", false, "DataRecord field name", "value")
                ),
                "Reads live numeric value directly from object variables.",
                List.of("hist.live('root.devices.pump01', 'temperature')"),
                List.of("cel", "live", "historian"),
                "core",
                "analytics-catalog-hist-live"
        ));
    }

    private static AnalyticsCatalogEntry histAggregate(String id, String displayName, String helper) {
        return new AnalyticsCatalogEntry(
                id,
                displayName,
                "A",
                List.of("cel", "historian"),
                "hist." + helper + "('<objectPath>', '<variable>', '<field?>', '<windowBucket?>')",
                List.of(
                        parameter("objectPath", "string", true, "Object path to query", null),
                        parameter("variable", "string", true, "Variable name", null),
                        parameter("field", "string", false, "DataRecord field name", "value"),
                        parameter("windowBucket", "string", false, "Historian bucket (e.g. 5m, 1h)", "5m")
                ),
                "Historian aggregate CEL helper: " + helper + ".",
                List.of("hist." + helper + "('root.devices.pump01', 'temperature', 'value', '5m')"),
                List.of("cel", "historian", "aggregate"),
                "core",
                "analytics-catalog-hist-" + helper
        );
    }

    private static void registerPresets(Map<String, AnalyticsCatalogEntry> catalog) {
        for (HistorianComputationPresets.Preset preset : HistorianComputationPresets.all()) {
            registerIfAbsent(catalog, new AnalyticsCatalogEntry(
                    preset.id(),
                    preset.displayName(),
                    "A",
                    List.of("preset", "binding-rule"),
                    preset.expressionTemplate(),
                    presetParameters(preset),
                    preset.description(),
                    List.of(preset.expressionTemplate()),
                    List.of("preset", "historian", preset.helper()),
                    "core",
                    "analytics-catalog-preset-" + preset.id().toLowerCase()
            ));
        }
    }

    private static void registerPackFunctions(
            Map<String, AnalyticsCatalogEntry> catalog,
            AnalyticsExtensionRegistry extensionRegistry
    ) {
        for (AnalyticsExtensionRegistry.RegisteredAnalyticsFunction function : extensionRegistry.registeredFunctions()) {
            registerIfAbsent(catalog, packEntry(function));
        }
    }

    private static void registerUserFormulas(
            Map<String, AnalyticsCatalogEntry> catalog,
            List<AnalyticsFormula> formulas
    ) {
        for (AnalyticsFormula formula : formulas) {
            registerIfAbsent(catalog, formulaEntry(formula));
        }
    }

    private static AnalyticsCatalogEntry formulaEntry(AnalyticsFormula formula) {
        List<AnalyticsCatalogParameter> parameters = formula.parameters() == null
                ? List.of()
                : formula.parameters().stream()
                        .map(param -> parameter(
                                param.name(),
                                param.type(),
                                param.required(),
                                param.description(),
                                param.defaultValue()
                        ))
                        .toList();
        String pack = AnalyticsFormula.SCOPE_APP.equals(formula.scope()) && formula.appId() != null
                ? "app:" + formula.appId()
                : "site";
        List<String> kinds = List.of(formula.kind());
        List<String> tags = new ArrayList<>(List.of("formula", "user", formula.kind()));
        if (AnalyticsFormula.SCOPE_APP.equals(formula.scope())) {
            tags.add("app");
        }
        return new AnalyticsCatalogEntry(
                formula.id(),
                formula.displayName(),
                "B",
                kinds,
                formula.expression(),
                parameters,
                "User-defined analytics formula.",
                List.of(formula.expression()),
                List.copyOf(tags),
                pack,
                "analytics-catalog-formula-" + formula.id().toLowerCase()
        );
    }

    private static AnalyticsCatalogEntry packEntry(AnalyticsExtensionRegistry.RegisteredAnalyticsFunction function) {
        AnalyticsFunctionDescriptor descriptor = function.descriptor();
        String id = descriptor.id() == null || descriptor.id().isBlank() ? function.helperId() : descriptor.id();
        String displayName = descriptor.displayName() == null || descriptor.displayName().isBlank()
                ? id
                : descriptor.displayName();
        List<String> tags = descriptor.tags() == null ? List.of("extension") : List.copyOf(descriptor.tags());
        String helper = descriptor.helper() == null || descriptor.helper().isBlank()
                ? function.helperId()
                : descriptor.helper();
        String syntax = descriptor.syntax() == null || descriptor.syntax().isBlank()
                ? helper + "(...)"
                : descriptor.syntax();
        List<AnalyticsCatalogParameter> parameters = descriptor.parameters() == null
                ? List.of()
                : descriptor.parameters().stream()
                        .map(param -> parameter(
                                param.name(),
                                param.type(),
                                param.required(),
                                param.description(),
                                null
                        ))
                        .toList();
        return new AnalyticsCatalogEntry(
                id,
                displayName,
                "C",
                List.of("helper", "binding-rule"),
                syntax,
                parameters,
                "Analytics extension function from pack " + function.packId() + ".",
                List.of(syntax),
                tags,
                function.packId(),
                "analytics-catalog-pack-" + id.toLowerCase()
        );
    }

    private static List<AnalyticsCatalogParameter> presetParameters(HistorianComputationPresets.Preset preset) {
        List<AnalyticsCatalogParameter> params = new ArrayList<>();
        params.add(parameter("objectPath", "string", true, "Source object path", null));
        params.add(parameter("sourceVariable", "string", true, "Source variable name", null));
        params.add(parameter("windowBucket", "string", false, "Historian bucket", preset.windowBucket()));
        if ("oee".equals(preset.helper())) {
            params.add(parameter("availabilityVariable", "string", false, "Availability metric variable", "availabilityPct"));
            params.add(parameter("performanceVariable", "string", false, "Performance metric variable", "performancePct"));
            params.add(parameter("qualityVariable", "string", false, "Quality metric variable", "qualityPct"));
        }
        return List.copyOf(params);
    }

    private static void registerIfAbsent(Map<String, AnalyticsCatalogEntry> catalog, AnalyticsCatalogEntry entry) {
        catalog.putIfAbsent(entry.id(), entry);
    }

    private static AnalyticsCatalogParameter parameter(
            String name,
            String type,
            boolean required,
            String description,
            String defaultValue
    ) {
        return new AnalyticsCatalogParameter(name, type, required, description, defaultValue);
    }
}
