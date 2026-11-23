package com.ispf.server.ai.agent;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Predicate;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Rejects finish answers that tell the user to run a discovery tool in prose
 * instead of emitting {@code {"type":"tool",...}} (Ask / Copilot failure mode).
 */
public final class AgentProseToolSuggestGuard {

    /** Read-only tools the model must call itself — not paste into chat for the user. */
    private static final List<String> DISCOVERY_TOOLS = List.of(
            "search_context",
            "list_drivers",
            "get_driver_help",
            "list_examples",
            "get_example_bundle",
            "get_widget_catalog",
            "get_automation_schema",
            "list_objects",
            "get_object",
            "list_variables",
            "describe_variables",
            "list_binding_rules",
            "list_object_blueprints",
            "list_applications",
            "list_reports",
            "get_report_schema",
            "list_mimic_symbols",
            "list_agent_tools",
            "describe_agent_tool",
            "enable_agent_tool_pack",
            "search_platform_recipes",
            "list_instance_types",
            "list_mixin_blueprints",
            "list_singleton_blueprints",
            "list_virtual_profiles"
    );

    private static final Set<String> DISCOVERY_TOOL_SET = Set.copyOf(DISCOVERY_TOOLS);

    private static final String TOOL_ALT = buildToolAlternation();

    private static final Pattern TOOL_TOKEN = Pattern.compile(
            "(?iu)(?<![a-z0-9_])(" + TOOL_ALT + ")(?![a-z0-9_])"
    );

    private static final Pattern IMPERATIVE_NEAR_TOOL = Pattern.compile(
            "(?isu)(?:используйте|используй|вызовите|вызови|можно использовать|рекомендуется использовать|"
                    + "\\b(?:please\\s+)?(?:use|call|run)\\b(?:\\s+the)?(?:\\s+tool)?|"
                    + "you\\s+(?:can|should|must|need\\s+to)\\s+(?:use|call|run)|"
                    + "try\\s+calling|invoke(?:\\s+the)?(?:\\s+tool|\\s+command)?)"
                    + ".{0,80}?(?<![a-z0-9_])(" + TOOL_ALT + ")(?![a-z0-9_])"
                    + "|(?<![a-z0-9_])(" + TOOL_ALT + ")(?![a-z0-9_]).{0,40}?"
                    + "(?:с параметром|with (?:parameter|arg)|driverId\\s*=)"
    );

    private static final Pattern CLI_LIKE_TOOL_LINE = Pattern.compile(
            "(?imu)^\\s*(?:[-*+]|\\d+[.)])?\\s*`?(" + TOOL_ALT + ")`?\\s+[\\w]+=\\S+"
    );

    private AgentProseToolSuggestGuard() {
    }

    private static String buildToolAlternation() {
        List<String> ordered = new ArrayList<>(DISCOVERY_TOOLS);
        ordered.sort(Comparator.comparingInt(String::length).reversed().thenComparing(s -> s));
        return String.join("|", ordered);
    }

    public static boolean alreadyNudged(List<Map<String, Object>> steps) {
        if (steps == null || steps.isEmpty()) {
            return false;
        }
        for (Map<String, Object> step : steps) {
            if (step != null && Boolean.TRUE.equals(step.get("proseToolSuggestNudge"))) {
                return true;
            }
        }
        return false;
    }

    public static Optional<String> rejectFinish(String finishSummary, List<Map<String, Object>> steps) {
        return rejectFinish(finishSummary, steps, tool -> true);
    }

    /**
     * @param isCallable only suggest tools the current surface can actually run
     *                   (Ask: all discovery; Copilot: allowlist / Settings focus)
     */
    public static Optional<String> rejectFinish(
            String finishSummary,
            List<Map<String, Object>> steps,
            Predicate<String> isCallable
    ) {
        if (finishSummary == null || finishSummary.isBlank()) {
            return Optional.empty();
        }
        Predicate<String> callable = isCallable == null ? tool -> true : isCallable;
        Set<String> suggested = suggestedDiscoveryTools(finishSummary);
        if (suggested.isEmpty()) {
            return Optional.empty();
        }
        Set<String> calledOk = successfulTools(steps);
        Set<String> missing = new LinkedHashSet<>();
        for (String tool : suggested) {
            if (!callable.test(tool)) {
                continue;
            }
            if (!calledOk.contains(tool)) {
                missing.add(tool);
            }
        }
        if (missing.isEmpty()) {
            return Optional.empty();
        }
        String first = missing.iterator().next();
        return Optional.of(
                "Finish rejected: do not tell the user to run `" + first + "` (or other discovery tools) in prose. "
                        + "Emit {\"type\":\"tool\",\"name\":\"" + first + "\",\"arguments\":{...}} yourself now, "
                        + "then finish with Markdown from the tool result. "
                        + "User-typed lines like `search_context topic=...` are NOT tool calls."
        );
    }

    static Set<String> suggestedDiscoveryTools(String summary) {
        Set<String> found = new LinkedHashSet<>();
        Matcher imperative = IMPERATIVE_NEAR_TOOL.matcher(summary);
        while (imperative.find()) {
            String a = imperative.group(1);
            String b = imperative.group(2);
            if (a != null) {
                found.add(a.toLowerCase(Locale.ROOT));
            }
            if (b != null) {
                found.add(b.toLowerCase(Locale.ROOT));
            }
        }
        Matcher cli = CLI_LIKE_TOOL_LINE.matcher(summary);
        while (cli.find()) {
            found.add(cli.group(1).toLowerCase(Locale.ROOT));
        }
        if (found.isEmpty() && looksLikeToolRedirect(summary)) {
            Matcher token = TOOL_TOKEN.matcher(summary);
            while (token.find()) {
                found.add(token.group(1).toLowerCase(Locale.ROOT));
            }
        }
        found.retainAll(DISCOVERY_TOOL_SET);
        return found;
    }

    private static boolean looksLikeToolRedirect(String summary) {
        String lower = summary.toLowerCase(Locale.ROOT);
        if (summary.length() > 900) {
            return false;
        }
        return containsAny(
                lower,
                "используйте",
                "используй",
                "вызовите",
                "вызови",
                "можно использовать",
                "для получения",
                "use the",
                "use tool",
                "use `",
                "call `",
                "run `",
                "you can use",
                "you should use",
                "call the",
                "run the",
                "try the tool"
        );
    }

    private static Set<String> successfulTools(List<Map<String, Object>> steps) {
        Set<String> ok = new LinkedHashSet<>();
        if (steps == null) {
            return ok;
        }
        for (Map<String, Object> step : steps) {
            if (step == null || !"tool".equals(String.valueOf(step.get("type")))) {
                continue;
            }
            Object resultRaw = step.get("result");
            if (!(resultRaw instanceof Map<?, ?> result)) {
                continue;
            }
            if (!"OK".equals(String.valueOf(result.get("status")))) {
                continue;
            }
            Object tool = step.get("tool");
            if (tool != null) {
                ok.add(String.valueOf(tool).trim().toLowerCase(Locale.ROOT));
            }
        }
        return ok;
    }

    private static boolean containsAny(String haystack, String... needles) {
        for (String needle : needles) {
            if (haystack.contains(needle)) {
                return true;
            }
        }
        return false;
    }
}
