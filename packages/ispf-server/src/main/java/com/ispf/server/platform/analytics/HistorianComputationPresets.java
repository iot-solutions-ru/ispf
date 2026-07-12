package com.ispf.server.platform.analytics;

import com.ispf.core.binding.BindingRuleKind;
import com.ispf.core.binding.BindingVariableRef;

import java.util.List;
import java.util.Map;

/**
 * Static historian computation presets — documentation-backed recipes, not object-tree templates (ADR-0041).
 */
public final class HistorianComputationPresets {

    private HistorianComputationPresets() {
    }

    public record Preset(
            String id,
            String displayName,
            String description,
            String helper,
            String expressionTemplate,
            String windowBucket,
            long periodicMs,
            String defaultTargetVariable
    ) {
    }

    public static List<Preset> all() {
        return List.of(
                new Preset(
                        "avg",
                        "Rolling average",
                        "Historian avg over a time window",
                        "avg",
                        "avg({objectPath}/{sourceVariable}, {windowBucket})",
                        "5m",
                        60_000L,
                        "avgValue"
                ),
                new Preset(
                        "rateOfChange",
                        "Rate of change",
                        "Delta between first and last bucket average",
                        "rateOfChange",
                        "rateOfChange({objectPath}/{sourceVariable}, {windowBucket})",
                        "1h",
                        60_000L,
                        "rocValue"
                ),
                new Preset(
                        "totalizer",
                        "Totalizer",
                        "Sum of bucket averages weighted by sample count",
                        "totalizer",
                        "totalizer({objectPath}/{sourceVariable}, {windowBucket})",
                        "1h",
                        60_000L,
                        "totalizedValue"
                ),
                new Preset(
                        "min",
                        "Window minimum",
                        "Minimum historian value in the selected window",
                        "min",
                        "min({objectPath}/{sourceVariable}, {windowBucket})",
                        "1h",
                        60_000L,
                        "minValue"
                ),
                new Preset(
                        "max",
                        "Window maximum",
                        "Maximum historian value in the selected window",
                        "max",
                        "max({objectPath}/{sourceVariable}, {windowBucket})",
                        "1h",
                        60_000L,
                        "maxValue"
                ),
                new Preset(
                        "last",
                        "Last value",
                        "Latest historian sample with live fallback",
                        "last",
                        "last({objectPath}/{sourceVariable}, {windowBucket})",
                        "1h",
                        60_000L,
                        "lastValue"
                ),
                new Preset(
                        "oee",
                        "OEE composite",
                        "Availability × Performance × Quality",
                        "oee",
                        "oee('{sourcePath}', '{availabilityVariable}', '{performanceVariable}', '{qualityVariable}', '{windowBucket}')",
                        "8h",
                        300_000L,
                        "oeePct"
                ),
                new Preset(
                        "customCel",
                        "Custom CEL",
                        "CEL expression with avg/live historian helpers",
                        "cel",
                        "avg({objectPath}/{sourceVariable}, 5m)",
                        "5m",
                        60_000L,
                        "computedValue"
                )
        );
    }

    public static Map<String, Object> materialize(String presetId, MaterializeRequest request) {
        Preset preset = all().stream()
                .filter(item -> item.id().equals(presetId))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Unknown preset: " + presetId));
        String objectPath = require(request.objectPath(), "objectPath");
        String sourceVariable = require(request.sourceVariable(), "sourceVariable");
        String sourcePath = blankToDefault(request.sourcePath(), objectPath);
        String windowBucket = blankToDefault(request.windowBucket(), preset.windowBucket());
        String targetVariable = blankToDefault(request.targetVariable(), preset.defaultTargetVariable());
        String expression = preset.expressionTemplate()
                .replace("{objectPath}", objectPath)
                .replace("{sourcePath}", sourcePath)
                .replace("{sourceVariable}", sourceVariable)
                .replace("{windowBucket}", windowBucket)
                .replace("{availabilityVariable}", blankToDefault(request.availabilityVariable(), "availabilityPct"))
                .replace("{performanceVariable}", blankToDefault(request.performanceVariable(), "performancePct"))
                .replace("{qualityVariable}", blankToDefault(request.qualityVariable(), "qualityPct"));
        String activatorRef = sourcePath + "/" + sourceVariable;
        return Map.of(
                "id", preset.id() + "-" + System.currentTimeMillis(),
                "name", preset.displayName(),
                "enabled", true,
                "order", 0,
                "kind", BindingRuleKind.HISTORIAN.name().toLowerCase(),
                "activators", Map.of(
                        "onStartup", false,
                        "onVariableChange", List.of(Map.of("ref", activatorRef)),
                        "onEvent", null,
                        "periodicMs", preset.periodicMs(),
                        "async", false,
                        "onContextChange", false
                ),
                "condition", "",
                "expression", expression,
                "target", Map.of(
                        "kind", "variable",
                        "variableName", targetVariable,
                        "field", "value"
                ),
                "windowBucket", windowBucket
        );
    }

    public record MaterializeRequest(
            String objectPath,
            String sourcePath,
            String sourceVariable,
            String windowBucket,
            String targetVariable,
            String availabilityVariable,
            String performanceVariable,
            String qualityVariable
    ) {
    }

    private static String require(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " is required");
        }
        return value;
    }

    private static String blankToDefault(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }
}
