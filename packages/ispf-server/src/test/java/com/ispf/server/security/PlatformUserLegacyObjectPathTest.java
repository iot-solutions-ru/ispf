package com.ispf.server.security;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class PlatformUserLegacyObjectPathTest {

    @Test
    void rewritesRootUsersLegacyPath() {
        assertThat(PlatformUserObjectTreeService.normalizeLegacyUserObjectPath(
                "dogfood-deploy",
                "root.users.dogfood-deploy"
        )).isEqualTo(PlatformUserService.USERS_PATH_PREFIX + "dogfood-deploy");
    }

    @Test
    void leavesCanonicalPathUnchanged() {
        String canonical = PlatformUserService.USERS_PATH_PREFIX + "admin";
        assertThat(PlatformUserObjectTreeService.normalizeLegacyUserObjectPath("admin", canonical))
                .isEqualTo(canonical);
    }

    @Test
    void leavesTenantUserPathUnchanged() {
        String tenantPath = "root.tenant.acme.platform.security.users.bob";
        assertThat(PlatformUserObjectTreeService.normalizeLegacyUserObjectPath("bob", tenantPath))
                .isEqualTo(tenantPath);
    }

    @Test
    void doesNotRewriteWhenUsernameDoesNotMatchSuffix() {
        assertThat(PlatformUserObjectTreeService.normalizeLegacyUserObjectPath(
                "alice",
                "root.users.bob"
        )).isEqualTo("root.users.bob");
    }
}
