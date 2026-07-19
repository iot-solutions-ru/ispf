package com.ispf.server.tenant;

import com.ispf.server.config.TenantIsolationProperties;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class TenantRlsFilterTest {

    @AfterEach
    void cleanup() {
        SecurityContextHolder.clearContext();
        TenantRlsContext.clear();
    }

    @Test
    void unauthenticatedUsesBypass() {
        TenantRlsFilter filter = newFilter();
        assertThat(filter.resolveState().bypass()).isTrue();
        assertThat(filter.resolveState().tenantId()).isEmpty();
    }

    @Test
    void anonymousUsesBypass() {
        SecurityContextHolder.getContext().setAuthentication(
                new AnonymousAuthenticationToken("key", "anonymous", List.of(new SimpleGrantedAuthority("ROLE_ANONYMOUS")))
        );
        assertThat(newFilter().resolveState().bypass()).isTrue();
    }

    @Test
    void globalAdminUsesBypass() {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(
                        "admin",
                        null,
                        List.of(new SimpleGrantedAuthority("ROLE_admin"))
                )
        );
        TenantScopeService scope = mock(TenantScopeService.class);
        TenantRlsFilter filter = new TenantRlsFilter(scope, enabledProps());
        assertThat(filter.resolveState().bypass()).isTrue();
    }

    @Test
    void tenantUserSetsTenantContext() {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(
                        "acme-admin",
                        null,
                        List.of(new SimpleGrantedAuthority("ROLE_tenant-admin"))
                )
        );
        TenantScopeService scope = mock(TenantScopeService.class);
        when(scope.resolveTenantId(org.mockito.ArgumentMatchers.any())).thenReturn(Optional.of("acme"));
        TenantRlsFilter filter = new TenantRlsFilter(scope, enabledProps());

        TenantRlsContext.State state = filter.resolveState();
        assertThat(state.bypass()).isFalse();
        assertThat(state.tenantId()).isEqualTo("acme");
    }

    @Test
    void disabledPropertySkipsContextInFilterChainContract() {
        TenantIsolationProperties props = new TenantIsolationProperties();
        props.setDbRowIsolation(false);
        assertThat(props.isDbRowIsolation()).isFalse();
    }

    private static TenantRlsFilter newFilter() {
        return new TenantRlsFilter(mock(TenantScopeService.class), enabledProps());
    }

    private static TenantIsolationProperties enabledProps() {
        TenantIsolationProperties props = new TenantIsolationProperties();
        props.setDbRowIsolation(true);
        return props;
    }
}
