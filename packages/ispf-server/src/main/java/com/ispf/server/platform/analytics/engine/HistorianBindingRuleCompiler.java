package com.ispf.server.platform.analytics.engine;

import com.ispf.analytics.engine.AnalyticsSourceRef;
import com.ispf.analytics.engine.AnalyticsTagDefinition;
import com.ispf.analytics.engine.HistorianTagPaths;
import com.ispf.core.binding.BindingActivators;
import com.ispf.core.binding.BindingRule;
import com.ispf.core.binding.BindingRuleKind;
import com.ispf.core.binding.BindingVariableRef;
import com.ispf.server.config.AnalyticsProperties;
import com.ispf.server.history.HistorianRollupBuckets;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Compiles {@link BindingRuleKind#HISTORIAN} binding rules into {@link AnalyticsTagDefinition} (ADR-0041).
 */
public final class HistorianBindingRuleCompiler {

    private static final Pattern BUILTIN_CALL = Pattern.compile(
            "^(avg|rateOfChange|totalizer|min|max|last|oee|live)\\s*\\((.*)\\)$",
            Pattern.CASE_INSENSITIVE | Pattern.DOTALL
    );

    private static final Pattern EXTENSION_CALL = Pattern.compile(
            "^([a-zA-Z][a-zA-Z0-9]*)\\s*\\((.*)\\)$",
            Pattern.DOTALL
    );

    private HistorianBindingRuleCompiler() {
    }

    public static Optional<AnalyticsTagDefinition> compile(
            String objectPath,
            BindingRule rule,
            AnalyticsProperties analyticsProperties
    ) {
        return compile(objectPath, rule, analyticsProperties, Set.of());
    }

    public static Optional<AnalyticsTagDefinition> compile(
            String objectPath,
            BindingRule rule,
            AnalyticsProperties analyticsProperties,
            Set<String> extensionHelpers
    ) {
        if (rule == null || !rule.isHistorian()) {
            return Optional.empty();
        }
        if (!rule.target().isVariable()) {
            return Optional.empty();
        }
        String outputVariable = rule.target().variableName();
        String tagPath = HistorianTagPaths.encode(objectPath, rule.id());
        String windowBucket = blankToDefault(rule.windowBucket(), "5m");
        List<String> rollupBuckets = rule.rollupBuckets() != null && !rule.rollupBuckets().isEmpty()
                ? rule.rollupBuckets()
                : HistorianRollupBuckets.defaultForWindow(windowBucket);
        long periodicMs = rule.activators().periodicMs() > 0
                ? rule.activators().periodicMs()
                : analyticsProperties.enginePeriodicMs();
        boolean onChange = hasHistorianSampleTrigger(rule.activators());

        String expression = rule.expression().trim();
        Matcher builtin = BUILTIN_CALL.matcher(expression);
        if (builtin.matches()) {
            return compileBuiltin(
                    tagPath,
                    builtin.group(1).toLowerCase(Locale.ROOT),
                    builtin.group(2),
                    windowBucket,
                    rollupBuckets,
                    periodicMs,
                    onChange,
                    rule.enabled(),
                    outputVariable
            );
        }
        Matcher extension = EXTENSION_CALL.matcher(expression);
        if (extension.matches() && extensionHelpers.contains(extension.group(1))) {
            return compileSingleSourceBuiltin(
                    tagPath,
                    extension.group(1),
                    splitArgs(extension.group(2)),
                    windowBucket,
                    rollupBuckets,
                    periodicMs,
                    onChange,
                    rule.enabled(),
                    outputVariable
            );
        }
        if (HistorianCelPreprocessor.isCelExpression(expression)) {
            List<AnalyticsSourceRef> sources = HistorianCelPreprocessor.extractSources(expression);
            return Optional.of(new AnalyticsTagDefinition(
                    tagPath,
                    "cel",
                    sources,
                    windowBucket,
                    rollupBuckets,
                    periodicMs,
                    onChange,
                    rule.enabled(),
                    outputVariable,
                    expression
            ));
        }
        return Optional.empty();
    }

    public static List<AnalyticsTagDefinition> compileAll(
            String objectPath,
            List<BindingRule> rules,
            AnalyticsProperties analyticsProperties
    ) {
        return compileAll(objectPath, rules, analyticsProperties, Set.of());
    }

    public static List<AnalyticsTagDefinition> compileAll(
            String objectPath,
            List<BindingRule> rules,
            AnalyticsProperties analyticsProperties,
            Set<String> extensionHelpers
    ) {
        List<AnalyticsTagDefinition> tags = new ArrayList<>();
        for (BindingRule rule : rules) {
            compile(objectPath, rule, analyticsProperties, extensionHelpers).ifPresent(tags::add);
        }
        return tags;
    }

    private static Optional<AnalyticsTagDefinition> compileBuiltin(
            String tagPath,
            String helper,
            String argsBody,
            String defaultWindow,
            List<String> rollupBuckets,
            long periodicMs,
            boolean onChange,
            boolean enabled,
            String outputVariable
    ) {
        List<String> args = splitArgs(argsBody);
        return switch (helper) {
            case "avg", "live" -> compileSingleSourceBuiltin(
                    tagPath, helper, args, defaultWindow, rollupBuckets, periodicMs, onChange, enabled, outputVariable
            );
            case "rateofchange" -> compileSingleSourceBuiltin(
                    tagPath, "rateOfChange", args, defaultWindow, rollupBuckets, periodicMs, onChange, enabled, outputVariable
            );
            case "totalizer" -> compileSingleSourceBuiltin(
                    tagPath, "totalizer", args, defaultWindow, rollupBuckets, periodicMs, onChange, enabled, outputVariable
            );
            case "min" -> compileSingleSourceBuiltin(
                    tagPath, "min", args, defaultWindow, rollupBuckets, periodicMs, onChange, enabled, outputVariable
            );
            case "max" -> compileSingleSourceBuiltin(
                    tagPath, "max", args, defaultWindow, rollupBuckets, periodicMs, onChange, enabled, outputVariable
            );
            case "last" -> compileSingleSourceBuiltin(
                    tagPath, "last", args, defaultWindow, rollupBuckets, periodicMs, onChange, enabled, outputVariable
            );
            case "oee" -> compileOee(tagPath, args, defaultWindow, rollupBuckets, periodicMs, onChange, enabled, outputVariable);
            default -> Optional.empty();
        };
    }

    private static Optional<AnalyticsTagDefinition> compileSingleSourceBuiltin(
            String tagPath,
            String helper,
            List<String> args,
            String defaultWindow,
            List<String> rollupBuckets,
            long periodicMs,
            boolean onChange,
            boolean enabled,
            String outputVariable
    ) {
        if (args.size() < 1) {
            return Optional.empty();
        }
        ParsedSourceRef source = parseSourceRef(args.get(0));
        if (source == null) {
            return Optional.empty();
        }
        String windowBucket = args.size() >= 2 ? args.get(1).trim() : defaultWindow;
        return Optional.of(new AnalyticsTagDefinition(
                tagPath,
                helper,
                List.of(new AnalyticsSourceRef(source.path(), source.variable(), source.field())),
                windowBucket,
                rollupBuckets,
                periodicMs,
                onChange,
                enabled,
                outputVariable
        ));
    }

    private static Optional<AnalyticsTagDefinition> compileOee(
            String tagPath,
            List<String> args,
            String defaultWindow,
            List<String> rollupBuckets,
            long periodicMs,
            boolean onChange,
            boolean enabled,
            String outputVariable
    ) {
        if (args.size() < 4) {
            return Optional.empty();
        }
        String sourcePath = unquote(args.get(0));
        String availability = unquote(args.get(1));
        String performance = unquote(args.get(2));
        String quality = unquote(args.get(3));
        String windowBucket = args.size() >= 5 ? args.get(4).trim() : defaultWindow;
        String field = "value";
        List<AnalyticsSourceRef> sources = List.of(
                new AnalyticsSourceRef(sourcePath, availability, field),
                new AnalyticsSourceRef(sourcePath, performance, field),
                new AnalyticsSourceRef(sourcePath, quality, field)
        );
        return Optional.of(new AnalyticsTagDefinition(
                tagPath,
                "oee",
                sources,
                windowBucket,
                rollupBuckets,
                periodicMs,
                false,
                enabled,
                outputVariable
        ));
    }

    private static boolean hasHistorianSampleTrigger(BindingActivators activators) {
        if (activators == null) {
            return false;
        }
        for (BindingVariableRef ref : activators.onVariableChange()) {
            if (ref != null && ref.variableName() != null && !ref.variableName().isBlank()) {
                return true;
            }
        }
        return false;
    }

    private static ParsedSourceRef parseSourceRef(String raw) {
        String trimmed = unquote(raw).trim();
        try {
            var ref = com.ispf.core.ref.PlatformRefParser.parseOptional(trimmed);
            if (ref.isPresent() && ref.get().isVariable()) {
                var resolved = ref.get();
                if (resolved.isCurrentObject()) {
                    return null;
                }
                return new ParsedSourceRef(resolved.object(), resolved.name(), resolved.field());
            }
        } catch (RuntimeException ignored) {
            return null;
        }
        return null;
    }

    private static List<String> splitArgs(String body) {
        if (body == null || body.isBlank()) {
            return List.of();
        }
        List<String> args = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        boolean inQuote = false;
        char quote = 0;
        int depth = 0;
        for (int i = 0; i < body.length(); i++) {
            char ch = body.charAt(i);
            if (inQuote) {
                current.append(ch);
                if (ch == quote) {
                    inQuote = false;
                }
                continue;
            }
            if (ch == '\'' || ch == '"') {
                inQuote = true;
                quote = ch;
                current.append(ch);
                continue;
            }
            if (ch == '(') {
                depth++;
                current.append(ch);
                continue;
            }
            if (ch == ')') {
                depth = Math.max(0, depth - 1);
                current.append(ch);
                continue;
            }
            if (ch == ',' && depth == 0) {
                args.add(current.toString().trim());
                current.setLength(0);
                continue;
            }
            current.append(ch);
        }
        if (!current.isEmpty()) {
            args.add(current.toString().trim());
        }
        return args;
    }

    private static String unquote(String value) {
        if (value == null) {
            return "";
        }
        String trimmed = value.trim();
        if ((trimmed.startsWith("'") && trimmed.endsWith("'"))
                || (trimmed.startsWith("\"") && trimmed.endsWith("\""))) {
            return trimmed.substring(1, trimmed.length() - 1);
        }
        return trimmed;
    }

    private static String blankToDefault(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }

    private record ParsedSourceRef(String path, String variable, String field) {
    }
}
