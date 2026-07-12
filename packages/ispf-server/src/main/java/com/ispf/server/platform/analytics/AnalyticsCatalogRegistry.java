package com.ispf.server.platform.analytics;

import com.ispf.analytics.engine.AnalyticsEvaluatorRegistry;
import com.ispf.analytics.spi.AnalyticsFunctionDescriptor;
import com.ispf.expression.PlatformBindingCatalog;
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
import java.util.Set;

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
        registerReactivePlatformBindings(catalog);
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
                case "avg" -> new AnalyticsCatalogEntry(
                        "avg",
                        "Rolling average",
                        "A",
                        List.of("historian"),
                        "avg(<objectPath/variable>, <windowBucket?>)",
                        List.of(
                                parameter("source", "string", true, "PlatformRef to source variable", null),
                                parameter("windowBucket", "string", false, "Historian bucket (e.g. 5m, 1h)", "5m")
                        ),
                        "Returns the latest historian window average for a source.",
                        List.of("avg(root.devices.pump01/temperature, 5m)"),
                        List.of("historian", "window", "builtin"),
                        "core",
                        "analytics-catalog-avg"
                );
                case "rateOfChange" -> new AnalyticsCatalogEntry(
                        "rateOfChange",
                        "Rate of change",
                        "A",
                        List.of("historian"),
                        "rateOfChange(<objectPath/variable>, <windowBucket?>)",
                        List.of(
                                parameter("source", "string", true, "PlatformRef to source variable", null),
                                parameter("windowBucket", "string", false, "Historian bucket (e.g. 5m, 1h)", "5m")
                        ),
                        "Calculates delta between first and last bucket averages in the window.",
                        List.of("rateOfChange(root.devices.pump01/flowRate, 1h)"),
                        List.of("historian", "delta", "builtin"),
                        "core",
                        "analytics-catalog-rateofchange"
                );
                case "totalizer" -> new AnalyticsCatalogEntry(
                        "totalizer",
                        "Totalizer",
                        "A",
                        List.of("historian"),
                        "totalizer(<objectPath/variable>, <windowBucket?>)",
                        List.of(
                                parameter("source", "string", true, "PlatformRef to source variable", null),
                                parameter("windowBucket", "string", false, "Historian bucket (e.g. 5m, 1h)", "5m")
                        ),
                        "Accumulates bucket averages over a window for counter-like metrics.",
                        List.of("totalizer(root.devices.meter01/energy, 1h)"),
                        List.of("historian", "accumulation", "builtin"),
                        "core",
                        "analytics-catalog-totalizer"
                );
                case "last" -> new AnalyticsCatalogEntry(
                        "last",
                        "Last sample",
                        "A",
                        List.of("historian"),
                        "last(<objectPath/variable>)",
                        List.of(parameter("source", "string", true, "PlatformRef to source variable", null)),
                        "Reads the most recent sample, with live fallback if historian is empty.",
                        List.of("last(root.devices.pump01/temperature)"),
                        List.of("historian", "latest", "builtin"),
                        "core",
                        "analytics-catalog-last"
                );
                case "min" -> new AnalyticsCatalogEntry(
                        "min",
                        "Minimum in window",
                        "A",
                        List.of("historian"),
                        "min(<objectPath/variable>, <windowBucket?>)",
                        List.of(
                                parameter("source", "string", true, "PlatformRef to source variable", null),
                                parameter("windowBucket", "string", false, "Historian bucket (e.g. 5m, 1h)", "5m")
                        ),
                        "Returns the minimum value across historian buckets in a window.",
                        List.of("min(root.devices.pump01/pressure, 30m)"),
                        List.of("historian", "extrema", "builtin"),
                        "core",
                        "analytics-catalog-min"
                );
                case "max" -> new AnalyticsCatalogEntry(
                        "max",
                        "Maximum in window",
                        "A",
                        List.of("historian"),
                        "max(<objectPath/variable>, <windowBucket?>)",
                        List.of(
                                parameter("source", "string", true, "PlatformRef to source variable", null),
                                parameter("windowBucket", "string", false, "Historian bucket (e.g. 5m, 1h)", "5m")
                        ),
                        "Returns the maximum value across historian buckets in a window.",
                        List.of("max(root.devices.pump01/pressure, 30m)"),
                        List.of("historian", "extrema", "builtin"),
                        "core",
                        "analytics-catalog-max"
                );
                case "oee" -> new AnalyticsCatalogEntry(
                        "oee",
                        "OEE composite",
                        "A",
                        List.of("historian"),
                        "oee('<sourcePath>', '<availability>', '<performance>', '<quality>', '<windowBucket?>')",
                        List.of(
                                parameter("sourcePath", "string", true, "Object path with OEE metric variables", null),
                                parameter("availability", "string", false, "Availability variable name", "availability"),
                                parameter("performance", "string", false, "Performance variable name", "performance"),
                                parameter("quality", "string", false, "Quality variable name", "quality"),
                                parameter("windowBucket", "string", false, "Historian bucket (e.g. 8h)", "8h")
                        ),
                        "Composite OEE percent (availability × performance × quality).",
                        List.of("oee('root.platform.devices.line01', 'availabilityPct', 'performancePct', 'qualityPct', '8h')"),
                        List.of("historian", "oee", "builtin"),
                        "core",
                        "analytics-catalog-oee"
                );
                default -> new AnalyticsCatalogEntry(
                        helper,
                        helper,
                        "A",
                        List.of("historian"),
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

    private static void registerReactivePlatformBindings(Map<String, AnalyticsCatalogEntry> catalog) {
        for (PlatformBindingCatalog.Entry binding : PlatformBindingCatalog.reactiveEntries()) {
            List<String> tags = new ArrayList<>(List.of("reactive", "binding", "cel"));
            if (binding.stateful()) {
                tags.add("stateful");
            }
            if (binding.category() != null && !binding.category().isBlank()) {
                tags.add(binding.category());
            }
            registerIfAbsent(catalog, new AnalyticsCatalogEntry(
                    binding.id(),
                    binding.displayName(),
                    "A",
                    List.of("reactive"),
                    binding.syntax(),
                    binding.parameters().stream()
                            .map(param -> parameter(
                                    param.name(),
                                    param.type(),
                                    param.required(),
                                    param.description(),
                                    param.defaultValue()
                            ))
                            .toList(),
                    binding.description(),
                    binding.examples() == null ? List.of(binding.syntax()) : List.copyOf(binding.examples()),
                    List.copyOf(tags),
                    "core",
                    "analytics-catalog-reactive-" + binding.id().toLowerCase()
            ));
        }
    }

    private static void registerPresets(Map<String, AnalyticsCatalogEntry> catalog) {
        for (HistorianComputationPresets.Preset preset : HistorianComputationPresets.all()) {
            registerIfAbsent(catalog, new AnalyticsCatalogEntry(
                    preset.id(),
                    preset.displayName(),
                    "A",
                    List.of("historian"),
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
                deriveKindsFromDescriptor(descriptor),
                syntax,
                parameters,
                "Analytics extension function from pack " + function.packId() + ".",
                List.of(syntax),
                tags,
                function.packId(),
                "analytics-catalog-pack-" + id.toLowerCase()
        );
    }

    private static List<String> deriveKindsFromDescriptor(AnalyticsFunctionDescriptor descriptor) {
        List<String> kinds = new ArrayList<>();
        Set<String> tags = descriptor.tags() == null ? Set.of() : descriptor.tags();
        for (String tag : tags) {
            if (tag == null || tag.isBlank()) {
                continue;
            }
            String normalized = tag.trim().toLowerCase();
            if (("historian".equals(normalized) || "reactive".equals(normalized) || "cel".equals(normalized))
                    && !kinds.contains(normalized)) {
                kinds.add(normalized);
            }
        }
        if (kinds.isEmpty()) {
            kinds.add("historian");
        }
        return List.copyOf(kinds);
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
