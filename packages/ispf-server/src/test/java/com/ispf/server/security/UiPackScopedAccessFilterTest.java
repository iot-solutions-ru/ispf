package com.ispf.server.security;

import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

import java.util.List;

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
}
