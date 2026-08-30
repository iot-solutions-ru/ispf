package com.ispf.server.federation;

import com.ispf.server.config.IspfRoles;
import com.ispf.server.tenant.DelegatedTenantAuthenticationDetails;
import com.ispf.server.tenant.TenantScopeService;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.web.server.ResponseStatusException;

import java.net.URI;
import java.net.http.HttpRequest;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class FederationDelegatedPrincipalTest {

    private final TenantScopeService tenants = mock(TenantScopeService.class);

    @Test
    void intersectsDelegatedRolesWithHttpChannelRoles() {
        Authentication channel = authentication("peer", "ROLE_operator", "ROLE_developer");

        Authentication delegated = FederationDelegatedPrincipal.installFromHttpChannel(
                channel,
                "alice",
                List.of("ROLE_admin", "ROLE_operator", "developer"),
                null,
                tenants
        );

        assertThat(delegated.getName()).isEqualTo("alice");
        assertThat(IspfRoles.extractRoles(delegated))
                .containsExactlyInAnyOrder(IspfRoles.OPERATOR, IspfRoles.DEVELOPER);
    }

    @Test
    void deniesEmptyNormalizedHttpRoles() {
        Authentication channel = authentication("peer", "ROLE_operator");

        assertForbidden(() -> FederationDelegatedPrincipal.installFromHttpChannel(
                channel,
                "alice",
                List.of("", " ", "ROLE_"),
                null,
                tenants
        ));
    }

    @Test
    void doesNotEscalateOperatorChannelToClaimedAdmin() {
        Authentication channel = authentication("peer", "ROLE_operator");

        Authentication delegated = FederationDelegatedPrincipal.installFromHttpChannel(
                channel,
                "alice",
                List.of("ROLE_admin", "operator"),
                null,
                tenants
        );

        assertThat(IspfRoles.extractRoles(delegated)).containsExactly(IspfRoles.OPERATOR);
    }

    @Test
    void deniesCrossTenantDelegationForNonAdminChannel() {
        Authentication channel = authentication("peer", "ROLE_operator");
        when(tenants.resolveTenantId(channel)).thenReturn(Optional.of("tenant-a"));

        assertForbidden(() -> FederationDelegatedPrincipal.installFromHttpChannel(
                channel,
                "alice",
                List.of("operator"),
                "tenant-b",
                tenants
        ));
    }

    @Test
    void deniesBlankTenantClaimOnHttpChannel() {
        Authentication channel = authentication("peer", "ROLE_operator");

        assertForbidden(() -> FederationDelegatedPrincipal.installFromHttpChannel(
                channel,
                "alice",
                List.of("operator"),
                " ",
                tenants
        ));
    }

    @Test
    void permitsTrustedTunnelDelegationWithoutRoles() {
        Authentication delegated = FederationDelegatedPrincipal.installFromTrustedChannel(
                "alice",
                List.of(),
                " Tenant-A "
        );

        assertThat(delegated.getName()).isEqualTo("alice");
        assertThat(delegated.getAuthorities()).isEmpty();
        assertThat(delegated.getDetails())
                .isEqualTo(new DelegatedTenantAuthenticationDetails("tenant-a"));
    }

    @Test
    void capturesSortedPrincipalAndAppliesHttpHeaders() {
        Authentication channel = authentication("alice", "ROLE_operator", "developer");
        when(tenants.resolveTenantId(channel)).thenReturn(Optional.of("tenant-a"));

        FederationDelegatedPrincipal.Snapshot snapshot =
                FederationDelegatedPrincipal.capture(channel, tenants);
        HttpRequest.Builder builder = HttpRequest.newBuilder(URI.create("https://peer.example"));
        FederationDelegatedPrincipal.applyHttpHeaders(builder, snapshot);
        HttpRequest request = builder.GET().build();

        assertThat(snapshot.roles()).containsExactly("developer", "operator");
        assertThat(request.headers().firstValue(FederationDelegatedPrincipal.HEADER_ON_BEHALF_USER))
                .contains("alice");
        assertThat(request.headers().firstValue(FederationDelegatedPrincipal.HEADER_ON_BEHALF_ROLES))
                .contains("developer,operator");
        assertThat(request.headers().firstValue(FederationDelegatedPrincipal.HEADER_ON_BEHALF_TENANT))
                .contains("tenant-a");
    }

    @Test
    void parsesAndNormalizesRolesHeader() {
        assertThat(FederationDelegatedPrincipal.parseRolesHeader(
                " ROLE_operator,developer,ROLE_operator, ,ROLE_ "
        )).containsExactly("operator", "developer");
    }

    private static Authentication authentication(String username, String... roles) {
        var authorities = Arrays.stream(roles)
                .map(SimpleGrantedAuthority::new)
                .toList();
        return new UsernamePasswordAuthenticationToken(username, "N/A", authorities);
    }

    private static void assertForbidden(Runnable invocation) {
        assertThatThrownBy(invocation::run)
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(error -> assertThat(((ResponseStatusException) error).getStatusCode())
                        .isEqualTo(HttpStatus.FORBIDDEN));
    }
}
