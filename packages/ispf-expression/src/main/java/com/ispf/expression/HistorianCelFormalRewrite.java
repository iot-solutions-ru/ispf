package com.ispf.expression;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Rewrites historian helper calls into {@code self.__histN} placeholders so boolean
 * CEL templates can be formally verified without expanding to current sample literals.
 *
 * <p>Identical helper call text maps to the same placeholder (correlated contradictions
 * like {@code avg(x,5m) > 80 && avg(x,5m) < 50} are detected). Distinct calls get
 * distinct placeholders.
 */
public final class HistorianCelFormalRewrite {

    private static final Pattern HIST_CALL = Pattern.compile(
            "(avg|min|max|last|sum|live)\\s*\\(([^)]*)\\)",
            Pattern.CASE_INSENSITIVE
    );

    private HistorianCelFormalRewrite() {
    }

    public record Result(String rewritten, boolean rewrittenAny, Map<String, String> placeholders) {
    }

    public static Result rewrite(String expression) {
        if (expression == null || expression.isBlank()) {
            return new Result(expression, false, Map.of());
        }
        Matcher matcher = HIST_CALL.matcher(expression);
        if (!matcher.find()) {
            return new Result(expression, false, Map.of());
        }
        matcher.reset();
        Map<String, String> byCall = new LinkedHashMap<>();
        StringBuffer out = new StringBuffer();
        while (matcher.find()) {
            String call = matcher.group(0);
            String key = call.replaceAll("\\s+", " ").toLowerCase();
            String placeholder = byCall.computeIfAbsent(key, ignored -> "self.__hist" + byCall.size());
            matcher.appendReplacement(out, Matcher.quoteReplacement(placeholder));
        }
        matcher.appendTail(out);
        Map<String, String> placeholders = new LinkedHashMap<>();
        byCall.forEach((call, placeholder) -> placeholders.put(placeholder, call));
        return new Result(out.toString(), true, Map.copyOf(placeholders));
    }
}
