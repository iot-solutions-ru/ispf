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
        if (expression == null || expression.isBlank()) {
            return expression;
        }
        String out = WORD_AND.matcher(expression.trim()).replaceAll(" && ");
        out = WORD_OR.matcher(out).replaceAll(" || ");
        return out;
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
