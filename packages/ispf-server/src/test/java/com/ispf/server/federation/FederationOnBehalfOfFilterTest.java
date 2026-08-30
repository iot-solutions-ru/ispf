package com.ispf.server.federation;

import tools.jackson.databind.ObjectMapper;
import com.ispf.server.security.acl.VariableAclRequestContext;
import com.ispf.server.tenant.TenantScopeService;
import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

class FederationOnBehalfOfFilterTest {

    private final TenantScopeService tenants = mock(TenantScopeService.class);
    private final FederationOnBehalfOfFilter filter =
            new FederationOnBehalfOfFilter(tenants, new ObjectMapper());

    @AfterEach
    void cleanup() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void installsDelegatedPrincipalWithRoleIntersection() throws Exception {
        Authentication channel = authentication("peer", "operator");
        SecurityContextHolder.getContext().setAuthentication(channel);
        MockHttpServletRequest request = requestWithDelegation("alice", "developer");
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain chain = mock(FilterChain.class);
        doAnswer(invocation -> {
            Authentication effective = SecurityContextHolder.getContext().getAuthentication();
            assertThat(effective.getName()).isEqualTo("alice");
            assertThat(effective.getAuthorities()).isEmpty();
            assertThat(VariableAclRequestContext.isMemberEnforced()).isTrue();
            assertThat(VariableAclRequestContext.requireAuthentication()).isSameAs(effective);
            return null;
        }).when(chain).doFilter(any(), any());

        filter.doFilter(request, response, chain);

        verify(chain).doFilter(request, response);
        assertThat(response.getStatus()).isEqualTo(200);
        assertThat(SecurityContextHolder.getContext().getAuthentication()).isSameAs(channel);
        assertThat(VariableAclRequestContext.isMemberEnforced()).isFalse();
    }

    @Test
    void rejectsDelegationWithoutAuthenticatedChannel() throws Exception {
        MockHttpServletRequest request = requestWithDelegation("alice", "operator");
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain chain = mock(FilterChain.class);

        filter.doFilter(request, response, chain);

        verifyNoInteractions(chain);
        assertThat(response.getStatus()).isEqualTo(403);
        assertThat(response.getContentType()).isEqualTo("application/json");
        assertThat(response.getContentAsString())
                .isEqualTo("{\"error\":\"Authenticated federation channel required\"}");
        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
    }

    @Test
    void passesThroughWhenDelegatedUserHeaderIsAbsent() throws Exception {
        Authentication channel = authentication("peer", "operator");
        SecurityContextHolder.getContext().setAuthentication(channel);
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/objects");
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain chain = mock(FilterChain.class);
        doAnswer(invocation -> {
            assertThat(SecurityContextHolder.getContext().getAuthentication()).isSameAs(channel);
            assertThat(VariableAclRequestContext.isMemberEnforced()).isFalse();
            return null;
        }).when(chain).doFilter(any(), any());

        filter.doFilter(request, response, chain);

        verify(chain).doFilter(request, response);
        assertThat(SecurityContextHolder.getContext().getAuthentication()).isSameAs(channel);
    }

    private static MockHttpServletRequest requestWithDelegation(String user, String roles) {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/objects");
        request.addHeader(FederationDelegatedPrincipal.HEADER_ON_BEHALF_USER, user);
        request.addHeader(FederationDelegatedPrincipal.HEADER_ON_BEHALF_ROLES, roles);
        return request;
    }

    private static Authentication authentication(String username, String role) {
        return new UsernamePasswordAuthenticationToken(
                username,
                "N/A",
                List.of(new SimpleGrantedAuthority("ROLE_" + role))
        );
    }
}
