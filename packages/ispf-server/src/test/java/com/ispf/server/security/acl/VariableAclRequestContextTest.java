package com.ispf.server.security.acl;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class VariableAclRequestContextTest {

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void nestedCallAsMemberRestoresOuterScopeAndClearsAfterward() {
        assertThat(VariableAclRequestContext.isMemberEnforced()).isFalse();

        String result = VariableAclRequestContext.callAsMember(() -> {
            assertThat(VariableAclRequestContext.isMemberEnforced()).isTrue();
            String nested = VariableAclRequestContext.callAsMember(() -> {
                assertThat(VariableAclRequestContext.isMemberEnforced()).isTrue();
                return "complete";
            });
            assertThat(VariableAclRequestContext.isMemberEnforced()).isTrue();
            return nested;
        });

        assertThat(result).isEqualTo("complete");
        assertThat(VariableAclRequestContext.isMemberEnforced()).isFalse();
    }

    @Test
    void runAsMemberClearsScopeWhenActionThrows() {
        assertThatThrownBy(() -> VariableAclRequestContext.runAsMember(() -> {
            assertThat(VariableAclRequestContext.isMemberEnforced()).isTrue();
            throw new IllegalStateException("failed");
        }))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("failed");

        assertThat(VariableAclRequestContext.isMemberEnforced()).isFalse();
    }

    @Test
    void requireAuthenticationRejectsMissingSecurityContextAuthentication() {
        SecurityContextHolder.clearContext();

        assertThatThrownBy(VariableAclRequestContext::requireAuthentication)
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(error -> {
                    ResponseStatusException exception = (ResponseStatusException) error;
                    assertThat(exception.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
                    assertThat(exception.getReason()).isEqualTo("Authentication required for variable ACL");
                });
    }

    @Test
    void explicitMemberAuthenticationIsAvailableWithoutSecurityContext() {
        Authentication authentication =
                UsernamePasswordAuthenticationToken.authenticated("operator", "n/a", List.of());

        Authentication resolved = VariableAclRequestContext.callAsMember(
                authentication,
                VariableAclRequestContext::requireAuthentication
        );

        assertThat(resolved).isSameAs(authentication);
        assertThatThrownBy(VariableAclRequestContext::requireAuthentication)
                .isInstanceOf(ResponseStatusException.class);
    }
}
