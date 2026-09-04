package com.ispf.server.migration;

import java.util.ArrayList;
import java.util.List;

/**
 * Splits SQL migration scripts into individual statements on {@code ;} boundaries.
 * Handles single-line multi-statement batches and respects single-quoted literals.
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
        for (int i = 0; i < sql.length(); i++) {
            char ch = sql.charAt(i);
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
            if (ch == ';' && !inSingleQuote) {
                appendStatement(statements, current);
                continue;
            }
            current.append(ch);
        }
        appendStatement(statements, current);
        return statements;
    }

    private static void appendStatement(List<String> statements, StringBuilder current) {
        String statement = current.toString().trim();
        current.setLength(0);
        if (!statement.isEmpty()) {
            statements.add(statement);
        }
    }
}
