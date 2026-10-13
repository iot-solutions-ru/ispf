package com.ispf.server.ai.agent;

import com.ispf.core.object.PlatformObject;

import java.util.Locale;
import java.util.regex.Pattern;

/**
 * Best-effort rewrite of course/LLM expression dialects onto ISPF binding/CEL forms.
 */
final class AgentExpressionCoercion {

    private static final Pattern WORD_AND = Pattern.compile("(?i)(?<=[)\\]\\w\"'])\\s+and\\s+(?=[(\\w\"'])");
    private static final Pattern WORD_OR = Pattern.compile("(?i)(?<=[)\\]\\w\"'])\\s+or\\s+(?=[(\\w\"'])");

    private AgentExpressionCoercion() {
    }

    /** CEL alert conditions: {@code and}/{@code or} → {@code &&}/{@code ||}. */
    static String coerceCelCondition(String expression) {
        return coerceCelCondition(expression, null);
    }

    /**
     * CEL alert conditions: word {@code and}/{@code or}, plus rewrite of obvious tautologies
     * ({@code true}, {@code 1==1}, {@code true || …}) to a discriminating watch comparison.
     */
    static String coerceCelCondition(String expression, String watchVariable) {
        if (expression == null || expression.isBlank()) {
            return expression;
        }
        String out = WORD_AND.matcher(expression.trim()).replaceAll(" && ");
        out = WORD_OR.matcher(out).replaceAll(" || ");
        if (isObviousTautology(out) && watchVariable != null && !watchVariable.isBlank()) {
            String rewritten = discriminatingConditionForWatch(watchVariable);
            if (rewritten != null) {
                return rewritten;
            }
        }
        return out;
    }

    /** {@code true}, {@code 1==1}, {@code true || …} — rejected by formal verification as tautology. */
    static boolean isObviousTautology(String expression) {
        if (expression == null || expression.isBlank()) {
            return false;
        }
        String n = expression.trim().toLowerCase(Locale.ROOT).replaceAll("\\s+", "");
        if ("true".equals(n) || "1==1".equals(n) || "!false".equals(n) || "!(!true)".equals(n)) {
            return true;
        }
        if ("true||false".equals(n) || "false||true".equals(n)) {
            return true;
        }
        return n.startsWith("true||") || n.endsWith("||true");
    }

    static String discriminatingConditionForWatch(String watchVariable) {
        if (watchVariable == null || watchVariable.isBlank()) {
            return null;
        }
        String w = watchVariable.trim();
        if (!w.matches("[A-Za-z_][A-Za-z0-9_]*")) {
            return null;
        }
        String lower = w.toLowerCase(Locale.ROOT);
        if (looksBooleanWatch(lower)) {
            return "self." + w + "[\"value\"] == true";
        }
        return "self." + w + "[\"value\"] > 0";
    }

    private static boolean looksBooleanWatch(String lowerName) {
        return lowerName.contains("error")
                || lowerName.contains("alarm")
                || lowerName.contains("enabled")
                || lowerName.contains("active")
                || lowerName.endsWith("flag")
                || lowerName.startsWith("is")
                || lowerName.equals("status")
                || lowerName.equals("ok");
    }

    /**
     * Binding expressions: rewrite invented table-sum dialects to
     * {@code sumRecordField(table, int)} (or telemetryTable / eventLog when present).
     */
    static String coerceBindingExpression(String expression, PlatformObject host) {
        if (expression == null || expression.isBlank()) {
            return expression;
        }
        String trimmed = expression.trim();
        if (looksLikeTableSumAttempt(trimmed)) {
            String table = pickTableVariable(host);
            if (table != null) {
                return "sumRecordField(" + table + ", int)";
            }
        }
        // Drop erroneous self. prefix on bare sumRecordField(self.table, …)
        if (trimmed.regionMatches(true, 0, "sumRecordField(self.", 0, "sumRecordField(self.".length())) {
            String rewritten = "sumRecordField(" + trimmed.substring("sumRecordField(self.".length());
            // also strip .rows if present as first arg: sumRecordField(table.rows, …)
            rewritten = rewritten.replaceFirst("^sumRecordField\\((\\w+)\\.rows\\s*,", "sumRecordField($1,");
            if (looksLikeValidPlatformBinding(rewritten)) {
                return rewritten;
            }
            String table = pickTableVariable(host);
            if (table != null) {
                return "sumRecordField(" + table + ", int)";
            }
        }
        return trimmed;
    }

    static String tableSumHint(PlatformObject host) {
        String table = pickTableVariable(host);
        String target = host != null && host.getVariable("tableIntSum").isPresent()
                ? "tableIntSum"
                : "<sumVar>";
        if (table == null) {
            table = "table";
        }
        return "Table column sums use platform binding sumRecordField(<tableVar>, int) — not CEL .map/.reduce. "
                + "Example: expression=sumRecordField(" + table + ", int) targetVariable=" + target + ". "
                + "virtual-lab already ships this rule as tableIntSum.";
    }

    private static boolean looksLikeTableSumAttempt(String expression) {
        String lower = expression.toLowerCase(Locale.ROOT);
        if (lower.contains("sumrecordfield") && (lower.contains("self.") || lower.contains(".rows"))) {
            return true;
        }
        return lower.contains(".rows.map")
                || lower.contains(".rows.reduce")
                || lower.contains(".rows.filter")
                || lower.contains("rows[*]")
                || lower.contains("rows.exists")
                || (lower.contains("sum(") && lower.contains("table"));
    }

    private static String pickTableVariable(PlatformObject host) {
        if (host == null) {
            return null;
        }
        for (String name : new String[]{"table", "telemetryTable", "eventLog"}) {
            if (host.getVariable(name).isPresent()) {
                return name;
            }
        }
        return null;
    }

    private static boolean looksLikeValidPlatformBinding(String expression) {
        try {
            com.ispf.expression.BindingExpressionValidator.validateOrThrow(expression);
            return true;
        } catch (RuntimeException ex) {
            return false;
        }
    }
}
