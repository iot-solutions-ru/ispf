package com.ispf.analytics.engine;

import java.util.List;
import java.util.Objects;

/**
 * Deployed analytics derived tag on an object path (BL-203).
 */
public record AnalyticsTagDefinition(
        String tagPath,
        String helper,
        List<AnalyticsSourceRef> sources,
        String windowBucket,
        List<String> rollupBuckets,
        long periodicMs,
        boolean onChangeEnabled,
        boolean enabled,
        String outputVariable,
        String expression
) {

    public static final String DEFAULT_OUTPUT = "derivedValue";

    public AnalyticsTagDefinition(
            String tagPath,
            String helper,
            List<AnalyticsSourceRef> sources,
            String windowBucket,
            List<String> rollupBuckets,
            long periodicMs,
            boolean onChangeEnabled,
            boolean enabled,
            String outputVariable
    ) {
        this(tagPath, helper, sources, windowBucket, rollupBuckets, periodicMs, onChangeEnabled, enabled, outputVariable, null);
    }

    public AnalyticsTagDefinition(
            String tagPath,
            String helper,
            List<AnalyticsSourceRef> sources,
            String windowBucket,
            long periodicMs,
            boolean onChangeEnabled,
            boolean enabled,
            String outputVariable
    ) {
        this(tagPath, helper, sources, windowBucket, List.of(), periodicMs, onChangeEnabled, enabled, outputVariable, null);
    }

    public AnalyticsTagDefinition(
            String tagPath,
            String helper,
            List<AnalyticsSourceRef> sources,
            String windowBucket,
            long periodicMs,
            boolean onChangeEnabled,
            boolean enabled,
            String outputVariable,
            String expression
    ) {
        this(tagPath, helper, sources, windowBucket, List.of(), periodicMs, onChangeEnabled, enabled, outputVariable, expression);
    }

    public AnalyticsTagDefinition {
        Objects.requireNonNull(tagPath, "tagPath");
        Objects.requireNonNull(helper, "helper");
        Objects.requireNonNull(sources, "sources");
        if (windowBucket == null || windowBucket.isBlank()) {
            windowBucket = "5m";
        }
        if (outputVariable == null || outputVariable.isBlank()) {
            outputVariable = DEFAULT_OUTPUT;
        }
        if (rollupBuckets == null || rollupBuckets.isEmpty()) {
            rollupBuckets = List.of("5m", "1h", "8h");
        } else {
            rollupBuckets = List.copyOf(rollupBuckets);
        }
        sources = List.copyOf(sources);
        if (expression != null && expression.isBlank()) {
            expression = null;
        }
    }

    public String objectPath() {
        return HistorianTagPaths.objectPath(tagPath);
    }

    public String ruleId() {
        return HistorianTagPaths.ruleId(tagPath);
    }

    public boolean isCelHelper() {
        return "cel".equalsIgnoreCase(helper) || "expression".equalsIgnoreCase(helper);
    }

    public String expressionOrEmpty() {
        return expression != null ? expression : "";
    }

    public AnalyticsSourceRef primarySource() {
        if (sources.isEmpty()) {
            throw new IllegalStateException("Tag has no sources: " + tagPath);
        }
        return sources.getFirst();
    }
}
