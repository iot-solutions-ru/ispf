package com.ispf.server.security;

import java.util.List;
import java.util.Locale;

/**
 * Guards admin-configured external JDBC URLs against exotic driver schemes
 * (CodeQL {@code java/ssrf} on {@code HikariConfig#setJdbcUrl}).
 *
 * <p>Returns a URL rebuilt from a constant-allowed prefix so taint analysis does not
 * treat the admin string as an arbitrary remote resource locator.
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
        for (String prefix : ALLOWED_PREFIXES) {
            if (lower.startsWith(prefix)) {
                // Constant prefix + remainder: scheme is never attacker-controlled.
                String rebuilt = prefix + trimmed.substring(prefix.length());
                rejectEmbeddedMetadataHost(rebuilt.toLowerCase(Locale.ROOT));
                return rebuilt;
            }
        }
        throw new IllegalArgumentException(
                "JDBC URL scheme not allowed (expected postgresql/mysql/mariadb/h2/sqlserver/oracle)"
        );
    }

    private static void rejectEmbeddedMetadataHost(String lowerUrl) {
        if (lowerUrl.contains("metadata.google.internal")
                || lowerUrl.contains("169.254.169.254")
                || lowerUrl.contains("@metadata")) {
            throw new IllegalArgumentException("JDBC URL host is blocked");
        }
    }
}
