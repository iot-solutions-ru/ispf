package com.ispf.server.security;

import java.util.List;
import java.util.Locale;

/**
 * Guards admin-configured external JDBC URLs against exotic driver schemes
 * (CodeQL {@code java/ssrf} on {@code HikariConfig#setJdbcUrl}).
 */
public final class JdbcUrlSafety {

    private static final List<String> ALLOWED_PREFIXES = List.of(
            "jdbc:postgresql:",
            "jdbc:pgsql:",
            "jdbc:mysql:",
            "jdbc:mariadb:",
            "jdbc:h2:",
            "jdbc:sqlserver:",
            "jdbc:oracle:"
    );

    private JdbcUrlSafety() {
    }

    public static String requireSafeJdbcUrl(String rawUrl) {
        if (rawUrl == null || rawUrl.isBlank()) {
            throw new IllegalArgumentException("JDBC URL is required");
        }
        String trimmed = rawUrl.trim();
        String lower = trimmed.toLowerCase(Locale.ROOT);
        boolean allowed = ALLOWED_PREFIXES.stream().anyMatch(lower::startsWith);
        if (!allowed) {
            throw new IllegalArgumentException(
                    "JDBC URL scheme not allowed (expected postgresql/mysql/mariadb/h2/sqlserver/oracle)"
            );
        }
        return trimmed;
    }
}
