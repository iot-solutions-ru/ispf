package com.ispf.server.tenant;

import com.ispf.server.datasource.DataSourceConnectionResolver;
import com.ispf.server.datasource.DataSourcePathResolver;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.util.Locale;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Prevents tenant callers (anyone with {@code tenant_id}, including tenant-admin) from using the
 * local/platform database. Tenants may only use external JDBC data sources with non-local hosts.
 * Global {@code admin} is unchanged.
 */
@Service
public class TenantLocalDataAccessGuard {

    private static final String LOCAL_DB_FORBIDDEN =
            "Tenant callers cannot use the local platform database; configure an external JDBC data source";
    private static final String BLANK_DS_FORBIDDEN =
            "Tenant callers must set dataSourcePath to an external JDBC data source "
                    + "(blank path falls through to the platform catalog)";
    private static final String LOCAL_JDBC_FORBIDDEN =
            "Tenant callers cannot use JDBC URLs targeting localhost, loopback, link-local, "
                    + "or the platform database host";
    private static final String INTERNAL_MODE_FORBIDDEN =
            "Tenant callers cannot use connectionMode=internal; only external JDBC is allowed";

    private static final Pattern AUTHORITY_HOST = Pattern.compile(
            "^[^/@]+@([^/:?]+)|^([^/:?]+)"
    );

    private final TenantScopeService tenantScopeService;
    private final DataSourceConnectionResolver connectionResolver;
    private final String platformJdbcHost;

    public TenantLocalDataAccessGuard(
            TenantScopeService tenantScopeService,
            DataSourceConnectionResolver connectionResolver,
            @Value("${spring.datasource.url:}") String platformJdbcUrl
    ) {
        this.tenantScopeService = tenantScopeService;
        this.connectionResolver = connectionResolver;
        this.platformJdbcHost = extractJdbcHost(platformJdbcUrl).orElse("");
    }

    public boolean isTenantCaller(Authentication auth) {
        return tenantScopeService.resolveTenantId(auth).isPresent();
    }

    public boolean isTenantCaller() {
        return isTenantCaller(currentAuthentication());
    }

    /** Throws 403 when the caller is a tenant user (has tenant_id). */
    public void requireExternalDataAccess(Authentication auth) {
        if (isTenantCaller(auth)) {
            throw forbidden(LOCAL_DB_FORBIDDEN);
        }
    }

    public void requireExternalDataAccess() {
        requireExternalDataAccess(currentAuthentication());
    }

    /**
     * For tenant callers: blank path and internal-mode data sources are forbidden.
     * Sole-tenant virtual {@code root.platform.data-sources.*} expands to the tenant tree and is
     * allowed when the data source is external JDBC.
     */
    public void requireAllowedDataSourcePath(String path, Authentication auth) {
        if (!isTenantCaller(auth)) {
            return;
        }
        if (path == null || path.isBlank()) {
            throw forbidden(BLANK_DS_FORBIDDEN);
        }
        String canonical = tenantScopeService.toCanonicalPath(path.trim(), auth);
        if (!connectionResolver.isExternal(canonical)) {
            throw forbidden(INTERNAL_MODE_FORBIDDEN);
        }
    }

    public void requireAllowedDataSourcePath(String path) {
        requireAllowedDataSourcePath(path, currentAuthentication());
    }

    /**
     * For tenant callers: reject localhost / loopback / link-local / platform datasource host.
     * No-op when the caller is not a tenant. Driver allowlist remains in
     * {@link DataSourceConnectionResolver}.
     */
    public void requireAllowedJdbcUrl(String jdbcUrl, Authentication auth) {
        if (!isTenantCaller(auth)) {
            return;
        }
        if (jdbcUrl == null || jdbcUrl.isBlank()) {
            throw forbidden(LOCAL_JDBC_FORBIDDEN);
        }
        if (isForbiddenJdbcUrlForTenant(jdbcUrl, platformJdbcHost)) {
            throw forbidden(LOCAL_JDBC_FORBIDDEN);
        }
    }

    public void requireAllowedJdbcUrl(String jdbcUrl) {
        requireAllowedJdbcUrl(jdbcUrl, currentAuthentication());
    }

    /** Rejects {@code connectionMode=internal} (or missing/unknown) for tenant callers. */
    public void requireExternalConnectionMode(String connectionMode, Authentication auth) {
        if (!isTenantCaller(auth)) {
            return;
        }
        if (!DataSourceConnectionResolver.MODE_EXTERNAL.equalsIgnoreCase(
                connectionMode != null ? connectionMode.trim() : "")) {
            throw forbidden(INTERNAL_MODE_FORBIDDEN);
        }
    }

    public void requireExternalConnectionMode(String connectionMode) {
        requireExternalConnectionMode(connectionMode, currentAuthentication());
    }

    public static boolean isPlatformDataSourcesPath(String path) {
        if (path == null || path.isBlank()) {
            return false;
        }
        String normalized = path.trim();
        return normalized.equals(DataSourcePathResolver.DATA_SOURCES_ROOT)
                || normalized.startsWith(DataSourcePathResolver.DATA_SOURCES_ROOT + ".");
    }

    static boolean isForbiddenJdbcUrlForTenant(String jdbcUrl, String platformJdbcHost) {
        String trimmed = jdbcUrl.trim();
        String lower = trimmed.toLowerCase(Locale.ROOT);
        if (isLocalH2Url(lower)) {
            return true;
        }
        Optional<String> host = extractJdbcHost(trimmed);
        if (host.isEmpty()) {
            // No parseable host — treat as local / unsafe for tenants.
            return true;
        }
        return isForbiddenHost(host.get(), platformJdbcHost);
    }

    static Optional<String> extractJdbcHost(String jdbcUrl) {
        if (jdbcUrl == null || jdbcUrl.isBlank()) {
            return Optional.empty();
        }
        String url = jdbcUrl.trim();
        String lower = url.toLowerCase(Locale.ROOT);

        // jdbc:h2:tcp://host[:port]/path]
        if (lower.startsWith("jdbc:h2:tcp://")) {
            return Optional.ofNullable(hostFromAuthority(url.substring("jdbc:h2:tcp://".length())));
        }

        // jdbc:oracle:thin:@//host:port/service or @host:port:sid
        if (lower.startsWith("jdbc:oracle:")) {
            int at = url.indexOf('@');
            if (at >= 0 && at + 1 < url.length()) {
                String rest = url.substring(at + 1);
                if (rest.startsWith("//")) {
                    return Optional.ofNullable(hostFromAuthority(rest.substring(2)));
                }
                return Optional.ofNullable(hostFromAuthority(rest));
            }
            return Optional.empty();
        }

        int schemeSep = url.indexOf("://");
        if (schemeSep >= 0) {
            return Optional.ofNullable(hostFromAuthority(url.substring(schemeSep + 3)));
        }
        return Optional.empty();
    }

    private static boolean isLocalH2Url(String lowerUrl) {
        if (!lowerUrl.startsWith("jdbc:h2:")) {
            return false;
        }
        // TCP H2 may be remote; host checks apply separately.
        return !lowerUrl.startsWith("jdbc:h2:tcp:");
    }

    private static String hostFromAuthority(String authorityAndPath) {
        if (authorityAndPath == null || authorityAndPath.isBlank()) {
            return null;
        }
        String authority = authorityAndPath;
        int slash = authority.indexOf('/');
        if (slash >= 0) {
            authority = authority.substring(0, slash);
        }
        int semicolon = authority.indexOf(';');
        if (semicolon >= 0) {
            authority = authority.substring(0, semicolon);
        }
        int question = authority.indexOf('?');
        if (question >= 0) {
            authority = authority.substring(0, question);
        }
        // userinfo@host
        int at = authority.lastIndexOf('@');
        if (at >= 0 && at + 1 < authority.length()) {
            authority = authority.substring(at + 1);
        }
        // IPv6 literals: [::1] or [fe80::1]:5432
        if (authority.startsWith("[")) {
            int end = authority.indexOf(']');
            if (end > 1) {
                return authority.substring(1, end);
            }
            return null;
        }
        Matcher matcher = AUTHORITY_HOST.matcher(authority);
        if (!matcher.find()) {
            return null;
        }
        String host = matcher.group(1) != null ? matcher.group(1) : matcher.group(2);
        return host == null || host.isBlank() ? null : host;
    }

    static boolean isForbiddenHost(String host, String platformJdbcHost) {
        if (host == null || host.isBlank()) {
            return true;
        }
        String h = host.trim().toLowerCase(Locale.ROOT);
        if ("localhost".equals(h) || "localhost.localdomain".equals(h)) {
            return true;
        }
        if ("::1".equals(h) || "0:0:0:0:0:0:0:1".equals(h)) {
            return true;
        }
        if (isIpv4Loopback(h) || isIpv4LinkLocal(h)) {
            return true;
        }
        if (h.startsWith("fe80:")) {
            return true;
        }
        if (platformJdbcHost != null && !platformJdbcHost.isBlank()
                && platformJdbcHost.equalsIgnoreCase(h)) {
            return true;
        }
        return false;
    }

    private static boolean isIpv4Loopback(String host) {
        String[] parts = host.split("\\.");
        if (parts.length != 4) {
            return false;
        }
        try {
            int first = Integer.parseInt(parts[0]);
            for (String part : parts) {
                int value = Integer.parseInt(part);
                if (value < 0 || value > 255) {
                    return false;
                }
            }
            return first == 127;
        } catch (NumberFormatException ex) {
            return false;
        }
    }

    private static boolean isIpv4LinkLocal(String host) {
        String[] parts = host.split("\\.");
        if (parts.length != 4) {
            return false;
        }
        try {
            int a = Integer.parseInt(parts[0]);
            int b = Integer.parseInt(parts[1]);
            for (String part : parts) {
                int value = Integer.parseInt(part);
                if (value < 0 || value > 255) {
                    return false;
                }
            }
            return a == 169 && b == 254;
        } catch (NumberFormatException ex) {
            return false;
        }
    }

    private static Authentication currentAuthentication() {
        return SecurityContextHolder.getContext().getAuthentication();
    }

    private static ResponseStatusException forbidden(String message) {
        return new ResponseStatusException(HttpStatus.FORBIDDEN, message);
    }
}
