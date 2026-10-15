package com.ispf.server.migration;

import java.util.ArrayList;
import java.util.List;

/**
 * Splits SQL migration scripts into individual statements on {@code ;} boundaries.
 * Respects single-quoted literals and PostgreSQL dollar-quoted strings ({@code $$} / {@code $tag$}).
 */
public final class SqlStatementSplitter {

    private SqlStatementSplitter() {
    }

    public static List<String> split(String sql) {
        if (sql == null || sql.isBlank()) {
            return List.of();
        }
        List<String> statements = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        boolean inSingleQuote = false;
        String dollarTag = null;
        for (int i = 0; i < sql.length(); i++) {
            char ch = sql.charAt(i);
            if (dollarTag != null) {
                if (ch == '$' && closesDollarQuote(sql, i, dollarTag)) {
                    int closerLength = dollarTag.length() + 2;
                    current.append(sql, i, i + closerLength);
                    i += closerLength - 1;
                    dollarTag = null;
                } else {
                    current.append(ch);
                }
                continue;
            }
            if (ch == '\'') {
                current.append(ch);
                if (inSingleQuote && i + 1 < sql.length() && sql.charAt(i + 1) == '\'') {
                    current.append('\'');
                    i++;
                } else {
                    inSingleQuote = !inSingleQuote;
                }
                continue;
            }
            if (!inSingleQuote && ch == '$') {
                String tag = openingDollarTag(sql, i);
                if (tag != null) {
                    int openerLength = tag.length() + 2;
                    current.append(sql, i, i + openerLength);
                    i += openerLength - 1;
                    dollarTag = tag;
                    continue;
                }
            }
            if (ch == ';' && !inSingleQuote) {
                appendStatement(statements, current);
                continue;
            }
            current.append(ch);
        }
        appendStatement(statements, current);
        return statements;
    }

    /** Tag between the two {@code $} of an opener, or {@code null} when {@code $} is not a dollar quote. */
    private static String openingDollarTag(String sql, int dollarIndex) {
        int end = dollarIndex + 1;
        while (end < sql.length() && isDollarTagChar(sql.charAt(end), end == dollarIndex + 1)) {
            end++;
        }
        if (end < sql.length() && sql.charAt(end) == '$') {
            return sql.substring(dollarIndex + 1, end);
        }
        return null;
    }

    private static boolean isDollarTagChar(char ch, boolean first) {
        if ((ch >= 'A' && ch <= 'Z') || (ch >= 'a' && ch <= 'z') || ch == '_') {
            return true;
        }
        return !first && ch >= '0' && ch <= '9';
    }

    private static boolean closesDollarQuote(String sql, int dollarIndex, String tag) {
        int end = dollarIndex + 1 + tag.length();
        return end < sql.length()
                && sql.charAt(end) == '$'
                && sql.regionMatches(dollarIndex + 1, tag, 0, tag.length());
    }

    private static void appendStatement(List<String> statements, StringBuilder current) {
        String statement = current.toString().trim();
        current.setLength(0);
        if (!statement.isEmpty()) {
            statements.add(statement);
        }
    }
}
