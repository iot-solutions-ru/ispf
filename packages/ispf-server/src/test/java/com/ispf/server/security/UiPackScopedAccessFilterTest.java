package com.ispf.server.security;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

import java.time.Instant;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class UiPackScopedAccessFilterTest {

    @Test
    void packScopedCannotCallAdminApis() {
        assertTrue(UiPackScopedAccessFilter.isAllowedForPack("/api/v1/bff/invoke"));
        assertFalse(UiPackScopedAccessFilter.isAllowedForPack("/api/v1/platform/runtime-settings"));
        assertFalse(UiPackScopedAccessFilter.isAllowedForPack("/api/v1/users"));
        assertFalse(UiPackScopedAccessFilter.isAllowedForPack("/api/v1/applications/demo/deploy"));
    }

    @Test
    void detectsUiPackRole() {
        var auth = new UsernamePasswordAuthenticationToken(
                "pack",
                "n/a",
                List.of(new SimpleGrantedAuthority(UiPackScopedAuthority.ROLE))
        );
        assertTrue(UiPackScopedAccessFilter.isUiPackScoped(auth));
    }

    @Test
    void jwtClaimDeniesAdminMutateAndAllowsMatchingBff() throws Exception {
        Jwt jwt = Jwt.withTokenValue("tok")
                .header("alg", "none")
                .subject("admin")
                .claim(UiPackScopedAuthority.CLAIM, "partner-app")
                .issuedAt(Instant.now())
                .expiresAt(Instant.now().plusSeconds(120))
                .build();
        var auth = new JwtAuthenticationToken(jwt, List.of(new SimpleGrantedAuthority("ROLE_admin")));
        SecurityContextHolder.getContext().setAuthentication(auth);
        try {
            assertEquals(403, filterStatus("POST", "/api/v1/security/users", null));
            assertEquals(200, filterStatus("POST", "/api/v1/bff/invoke", null));
            assertEquals(403, filterStatus(
                    "POST",
                    "/api/v1/bff/invoke",
                    "http://localhost/apps/other-app/"
            ));
        } finally {
            SecurityContextHolder.clearContext();
        }
    }

    private static int filterStatus(String method, String path, String referer) throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest(method, path);
        request.setRequestURI(path);
        if (referer != null) {
            request.addHeader("Referer", referer);
        }
        MockHttpServletResponse response = new MockHttpServletResponse();
        AtomicBoolean continued = new AtomicBoolean();
        new UiPackScopedAccessFilter().doFilter(request, response, (req, res) -> {
            continued.set(true);
            ((MockHttpServletResponse) res).setStatus(200);
        });
        if (!continued.get() && response.getStatus() == 200) {
            return 403;
        }
        return response.getStatus();
    }
}
