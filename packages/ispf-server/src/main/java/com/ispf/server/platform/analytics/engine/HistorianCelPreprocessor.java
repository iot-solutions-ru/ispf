package com.ispf.server.platform.analytics.engine;

import com.ispf.analytics.engine.AnalyticsSourceRef;
import com.ispf.analytics.engine.HistorianPort;
import com.ispf.analytics.engine.LiveVariablePort;
import com.ispf.server.history.VariableHistoryService;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Expands {@code hist.*(...)} calls in analytics CEL expressions to numeric literals (BL-211).
 *
 * <p>Example: {@code hist.avg('root.devices.a', 'temperature', '5m') * 1.8 + 32}
 */
public final class HistorianCelPreprocessor {

    private static final Pattern HIST_CALL = Pattern.compile(
            "hist\\.(avg|min|max|last|sum|live)\\s*\\(([^)]*)\\)",
            Pattern.CASE_INSENSITIVE
    );

    private static final Pattern BUCKET = Pattern.compile("^\\d+[mhdw]$", Pattern.CASE_INSENSITIVE);

    private HistorianCelPreprocessor() {
    }

    public static List<AnalyticsSourceRef> extractSources(String expression) {
        if (expression == null || expression.isBlank()) {
            return List.of();
        }
        Set<String> seen = new LinkedHashSet<>();
        List<AnalyticsSourceRef> sources = new ArrayList<>();
        Matcher matcher = HIST_CALL.matcher(expression);
        while (matcher.find()) {
            String function = matcher.group(1).toLowerCase(Locale.ROOT);
            if ("live".equals(function)) {
                continue;
            }
            List<String> args = parseArgs(matcher.group(2));
            if (args.size() < 2) {
                continue;
            }
            String objectPath = args.get(0);
            String variable = args.get(1);
            String field = resolveField(args);
            String key = objectPath + "\0" + variable + "\0" + field;
            if (seen.add(key)) {
                sources.add(new AnalyticsSourceRef(objectPath, variable, field));
            }
        }
        return List.copyOf(sources);
    }

    public static String expand(
            String expression,
            HistorianPort historian,
            LiveVariablePort live,
            Instant asOf
    ) {
        if (expression == null || expression.isBlank()) {
            throw new IllegalArgumentException("Expression is required");
        }
        Matcher matcher = HIST_CALL.matcher(expression);
        StringBuilder output = new StringBuilder();
        int lastEnd = 0;
        while (matcher.find()) {
            output.append(expression, lastEnd, matcher.start());
            String function = matcher.group(1).toLowerCase(Locale.ROOT);
            List<String> args = parseArgs(matcher.group(2));
            double value = evaluateCall(function, args, historian, live, asOf);
            output.append(formatNumber(value));
            lastEnd = matcher.end();
        }
        if (lastEnd == 0) {
            return normalizeIntegerLiterals(expression);
        }
        output.append(expression.substring(lastEnd));
        return normalizeIntegerLiterals(output.toString());
    }

    /**
     * CEL requires consistent numeric types; historian expansions emit doubles while user literals may be ints.
     */
    static String normalizeIntegerLiterals(String expression) {
        if (expression == null || expression.isBlank()) {
            return expression;
        }
        return INT_LITERAL.matcher(expression).replaceAll("$1.0");
    }

    private static final Pattern INT_LITERAL = Pattern.compile("(?<![.\\d])(\\d+)(?![.\\d])");

    private static double evaluateCall(
            String function,
            List<String> args,
            HistorianPort historian,
            LiveVariablePort live,
            Instant asOf
    ) {
        if (args.size() < 2) {
            throw new IllegalArgumentException("hist." + function + " requires at least objectPath and variable");
        }
        String objectPath = args.get(0);
        String variable = args.get(1);
        return switch (function) {
            case "live" -> live.readNumeric(objectPath, variable, resolveLiveField(args))
                    .orElseThrow(() -> new IllegalStateException(
                            "No live value for " + objectPath + "." + variable
                    ));
            case "avg", "min", "max", "sum", "last" -> aggregate(function, args, historian, asOf);
            default -> throw new IllegalArgumentException("Unsupported hist function: " + function);
        };
    }

    private static double aggregate(
            String function,
            List<String> args,
            HistorianPort historian,
            Instant asOf
    ) {
        String objectPath = args.get(0);
        String variable = args.get(1);
        String field = resolveField(args);
        String windowBucket = resolveWindowBucket(args);
        Instant to = asOf != null ? asOf : Instant.now();
        Duration window = VariableHistoryService.parseBucket(windowBucket);
        Instant from = to.minus(window);
        int maxBuckets = Math.max(4, (int) Math.ceil((double) window.toSeconds() / window.toSeconds()) + 2);

        if ("last".equals(function)) {
            List<HistorianPort.HistorianSample> samples = historian.query(
                    objectPath,
                    variable,
                    field,
                    from,
                    to,
                    1
            );
            if (samples.isEmpty() || samples.getFirst().value() == null) {
                throw new IllegalStateException("No historian samples for hist.last(" + objectPath + ", " + variable + ")");
            }
            return samples.getFirst().value();
        }

        List<HistorianPort.HistorianBucket> buckets = historian.aggregate(
                objectPath,
                variable,
                field,
                from,
                to,
                windowBucket,
                maxBuckets
        );
        if (buckets.isEmpty()) {
            throw new IllegalStateException("No historian buckets for hist." + function + "(" + objectPath + ", " + variable + ")");
        }
        return switch (function) {
            case "avg" -> requireFinite(latestMetric(buckets, HistorianPort.HistorianBucket::avg), function, objectPath, variable);
            case "min" -> requireFinite(latestMetric(buckets, HistorianPort.HistorianBucket::min), function, objectPath, variable);
            case "max" -> requireFinite(latestMetric(buckets, HistorianPort.HistorianBucket::max), function, objectPath, variable);
            case "sum" -> buckets.stream()
                    .map(HistorianPort.HistorianBucket::avg)
                    .filter(value -> value != null && !value.isNaN())
                    .mapToDouble(Double::doubleValue)
                    .sum();
            default -> throw new IllegalArgumentException("Unsupported aggregate: " + function);
        };
    }

    private static Double latestMetric(
            List<HistorianPort.HistorianBucket> buckets,
            java.util.function.Function<HistorianPort.HistorianBucket, Double> metric
    ) {
        for (int index = buckets.size() - 1; index >= 0; index--) {
            Double value = metric.apply(buckets.get(index));
            if (value != null && !value.isNaN()) {
                return value;
            }
        }
        return null;
    }

    private static double requireFinite(Double value, String function, String objectPath, String variable) {
        if (value == null || value.isNaN() || value.isInfinite()) {
            throw new IllegalStateException("hist." + function + "(" + objectPath + ", " + variable + ") returned no data");
        }
        return value;
    }

    private static String resolveField(List<String> args) {
        if (args.size() >= 4) {
            return args.get(2);
        }
        if (args.size() == 3 && !isBucket(args.get(2))) {
            return args.get(2);
        }
        return "value";
    }

    private static String resolveLiveField(List<String> args) {
        if (args.size() >= 3) {
            return args.get(2);
        }
        return "value";
    }

    private static String resolveWindowBucket(List<String> args) {
        if (args.size() >= 4) {
            return args.get(3);
        }
        if (args.size() == 3 && isBucket(args.get(2))) {
            return args.get(2);
        }
        return "5m";
    }

    private static boolean isBucket(String value) {
        return value != null && BUCKET.matcher(value.trim()).matches();
    }

    static List<String> parseArgs(String raw) {
        List<String> args = new ArrayList<>();
        if (raw == null || raw.isBlank()) {
            return args;
        }
        StringBuilder current = new StringBuilder();
        boolean inQuote = false;
        char quote = 0;
        for (int index = 0; index < raw.length(); index++) {
            char ch = raw.charAt(index);
            if (inQuote) {
                if (ch == quote) {
                    inQuote = false;
                    args.add(current.toString());
                    current.setLength(0);
                } else {
                    current.append(ch);
                }
                continue;
            }
            if (ch == '\'' || ch == '"') {
                inQuote = true;
                quote = ch;
                continue;
            }
            if (ch == ',') {
                continue;
            }
            if (!Character.isWhitespace(ch)) {
                current.append(ch);
            }
        }
        if (!current.isEmpty()) {
            args.add(current.toString());
        }
        return args;
    }

    private static String formatNumber(double value) {
        if (value == Math.rint(value) && !Double.isInfinite(value) && Math.abs(value) < 1e15) {
            return Long.toString((long) value) + ".0";
        }
        return Double.toString(value);
    }
}
