package com.ispf.server.tenant;

import com.ispf.server.config.IspfRoles;
import com.ispf.server.config.TenantIsolationProperties;
import com.ispf.server.datasource.DataSourceConnectionResolver;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class TenantLocalDataAccessGuardTest {

    @Test
    void isTenantCallerDetectsTenantAdminAndBypassesGlobalAdmin() {
        TenantLocalDataAccessGuard guard = newGuard(Optional.of("acme"), "jdbc:h2:mem:platform");

        assertThat(guard.isTenantCaller(tenantAdmin())).isTrue();
        assertThat(guard.isTenantCaller(globalAdmin())).isFalse();
        assertThat(guard.isTenantCaller(null)).isFalse();
    }

    @Test
    void requireExternalDataAccessDeniesTenant() {
        TenantLocalDataAccessGuard guard = newGuard(Optional.of("acme"), "jdbc:postgresql://db.internal/ispf");

        assertThatThrownBy(() -> guard.requireExternalDataAccess(tenantAdmin()))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("403")
                .hasMessageContaining("local platform database");

        assertThatCode(() -> guard.requireExternalDataAccess(globalAdmin()))
                .doesNotThrowAnyException();
    }

    @Test
    void requireAllowedDataSourcePathDeniesBlankAndInternal_allowsVirtualExternal() {
        DataSourceConnectionResolver resolver = mock(DataSourceConnectionResolver.class);
        when(resolver.isExternal("root.tenant.acme.platform.data-sources.remote")).thenReturn(true);
        when(resolver.isExternal("root.tenant.acme.platform.data-sources.local")).thenReturn(false);
        when(resolver.isExternal("root.tenant.acme.platform.data-sources.demo")).thenReturn(true);
        TenantLocalDataAccessGuard guard = newGuard(Optional.of("acme"), resolver, "jdbc:postgresql://db.internal/ispf");

        // Sole-tenant: virtual root.platform.data-sources.* expands into the tenant tree.
        assertThatCode(() -> guard.requireAllowedDataSourcePath(
                "root.platform.data-sources.demo", tenantAdmin()))
                .doesNotThrowAnyException();

        assertThatThrownBy(() -> guard.requireAllowedDataSourcePath("", tenantAdmin()))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("dataSourcePath");

        assertThatThrownBy(() -> guard.requireAllowedDataSourcePath(
                "root.tenant.acme.platform.data-sources.local", tenantAdmin()))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("connectionMode=internal");

        assertThatCode(() -> guard.requireAllowedDataSourcePath(
                "root.tenant.acme.platform.data-sources.remote", tenantAdmin()))
                .doesNotThrowAnyException();

        assertThatCode(() -> guard.requireAllowedDataSourcePath(
                "root.platform.data-sources.demo", globalAdmin()))
                .doesNotThrowAnyException();
    }

    @Test
    void requireAllowedJdbcUrlRejectsLoopbackLinkLocalAndPlatformHost() {
        TenantLocalDataAccessGuard guard = newGuard(
                Optional.of("acme"),
                "jdbc:postgresql://db.platform.example:5432/ispf"
        );

        assertThatThrownBy(() -> guard.requireAllowedJdbcUrl(
                "jdbc:postgresql://127.0.0.1:5432/tenant", tenantAdmin()))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("localhost");

        assertThatThrownBy(() -> guard.requireAllowedJdbcUrl(
                "jdbc:postgresql://localhost/tenant", tenantAdmin()))
                .isInstanceOf(ResponseStatusException.class);

        assertThatThrownBy(() -> guard.requireAllowedJdbcUrl(
                "jdbc:postgresql://[::1]/tenant", tenantAdmin()))
                .isInstanceOf(ResponseStatusException.class);

        assertThatThrownBy(() -> guard.requireAllowedJdbcUrl(
                "jdbc:postgresql://169.254.1.1/tenant", tenantAdmin()))
                .isInstanceOf(ResponseStatusException.class);

        assertThatThrownBy(() -> guard.requireAllowedJdbcUrl(
                "jdbc:postgresql://db.platform.example:5432/other", tenantAdmin()))
                .isInstanceOf(ResponseStatusException.class);

        assertThatThrownBy(() -> guard.requireAllowedJdbcUrl(
                "jdbc:h2:mem:tenant", tenantAdmin()))
                .isInstanceOf(ResponseStatusException.class);

        assertThatCode(() -> guard.requireAllowedJdbcUrl(
                "jdbc:postgresql://tenant-db.example.com:5432/app", tenantAdmin()))
                .doesNotThrowAnyException();

        assertThatCode(() -> guard.requireAllowedJdbcUrl(
                "jdbc:postgresql://127.0.0.1/ispf", globalAdmin()))
                .doesNotThrowAnyException();
    }

    @Test
    void requireAllowedJdbcUrlRejectsPrivateRangesByDefault() {
        TenantLocalDataAccessGuard guard = newGuard(Optional.of("acme"), "jdbc:postgresql://db.internal/ispf");

        assertThatThrownBy(() -> guard.requireAllowedJdbcUrl(
                "jdbc:postgresql://10.1.2.3:5432/tenant", tenantAdmin()))
                .isInstanceOf(ResponseStatusException.class);

        assertThatThrownBy(() -> guard.requireAllowedJdbcUrl(
                "jdbc:postgresql://172.16.0.10/tenant", tenantAdmin()))
                .isInstanceOf(ResponseStatusException.class);

        assertThatThrownBy(() -> guard.requireAllowedJdbcUrl(
                "jdbc:postgresql://192.168.1.5/tenant", tenantAdmin()))
                .isInstanceOf(ResponseStatusException.class);

        assertThatThrownBy(() -> guard.requireAllowedJdbcUrl(
                "jdbc:postgresql://[fd00::1]/tenant", tenantAdmin()))
                .isInstanceOf(ResponseStatusException.class);

        assertThatThrownBy(() -> guard.requireAllowedJdbcUrl(
                "jdbc:postgresql://[::ffff:192.168.1.5]/tenant", tenantAdmin()))
                .isInstanceOf(ResponseStatusException.class);

        // 172.16/12 boundary neighbours are public.
        assertThatCode(() -> guard.requireAllowedJdbcUrl(
                "jdbc:postgresql://172.15.0.10/tenant", tenantAdmin()))
                .doesNotThrowAnyException();
        assertThatCode(() -> guard.requireAllowedJdbcUrl(
                "jdbc:postgresql://172.32.0.10/tenant", tenantAdmin()))
                .doesNotThrowAnyException();
    }

    @Test
    void requireAllowedJdbcUrlAllowsPrivateRangesWhenExplicitlyEnabled() {
        TenantLocalDataAccessGuard guard = newGuard(
                Optional.of("acme"),
                mock(DataSourceConnectionResolver.class),
                "jdbc:postgresql://db.internal/ispf",
                true
        );

        assertThatCode(() -> guard.requireAllowedJdbcUrl(
                "jdbc:postgresql://192.168.1.5/tenant", tenantAdmin()))
                .doesNotThrowAnyException();

        // Loopback stays forbidden even when private ranges are allowed.
        assertThatThrownBy(() -> guard.requireAllowedJdbcUrl(
                "jdbc:postgresql://127.0.0.1/tenant", tenantAdmin()))
                .isInstanceOf(ResponseStatusException.class);
    }

    @Test
    void requireExternalConnectionModeDeniesInternalForTenant() {
        TenantLocalDataAccessGuard guard = newGuard(Optional.of("acme"), "jdbc:h2:mem:x");

        assertThatThrownBy(() -> guard.requireExternalConnectionMode("internal", tenantAdmin()))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("connectionMode=internal");

        assertThatCode(() -> guard.requireExternalConnectionMode("external", tenantAdmin()))
                .doesNotThrowAnyException();
        assertThatCode(() -> guard.requireExternalConnectionMode("internal", globalAdmin()))
                .doesNotThrowAnyException();
    }

    @Test
    void extractJdbcHostParsesCommonUrls() {
        assertThat(TenantLocalDataAccessGuard.extractJdbcHost("jdbc:postgresql://db.example:5432/app"))
                .contains("db.example");
        assertThat(TenantLocalDataAccessGuard.extractJdbcHost("jdbc:postgresql://user:pass@db.example/app"))
                .contains("db.example");
        assertThat(TenantLocalDataAccessGuard.extractJdbcHost("jdbc:sqlserver://sql.example:1433;databaseName=x"))
                .contains("sql.example");
        assertThat(TenantLocalDataAccessGuard.extractJdbcHost("jdbc:h2:tcp://remote-h2:9092/mem:x"))
                .contains("remote-h2");
        assertThat(TenantLocalDataAccessGuard.extractJdbcHost("jdbc:h2:mem:ispf")).isEmpty();
    }

    private static TenantLocalDataAccessGuard newGuard(Optional<String> tenantId, String platformUrl) {
        return newGuard(tenantId, mock(DataSourceConnectionResolver.class), platformUrl);
    }

    private static TenantLocalDataAccessGuard newGuard(
            Optional<String> tenantId,
            DataSourceConnectionResolver resolver,
            String platformUrl
    ) {
        return newGuard(tenantId, resolver, platformUrl, false);
    }

    private static TenantLocalDataAccessGuard newGuard(
            Optional<String> tenantId,
            DataSourceConnectionResolver resolver,
            String platformUrl,
            boolean allowPrivateAddresses
    ) {
        TenantIsolationProperties properties = new TenantIsolationProperties();
        TenantStore tenantStore = mock(TenantStore.class);
        when(tenantStore.findTenantIdForUser("ta")).thenReturn(tenantId);
        TenantScopeService scope = new TenantScopeService(
                tenantStore,
                properties,
                new TenantIsolationValidator(properties, mock(JdbcTemplate.class))
        );
        return new TenantLocalDataAccessGuard(scope, resolver, platformUrl, allowPrivateAddresses);
    }

    private static Authentication tenantAdmin() {
        return new UsernamePasswordAuthenticationToken(
                "ta",
                "n/a",
                List.of(new SimpleGrantedAuthority("ROLE_" + IspfRoles.TENANT_ADMIN))
        );
    }

    private static Authentication globalAdmin() {
        return new UsernamePasswordAuthenticationToken(
                "admin",
                "n/a",
                List.of(new SimpleGrantedAuthority("ROLE_" + IspfRoles.ADMIN))
        );
    }
}
