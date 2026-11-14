package com.ispf.server.config;

import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class StartupSecurityGuardTest {

    @Test
    void relaxedLocalProfileDoesNotThrow() {
        MockEnvironment environment = new MockEnvironment();
        environment.setActiveProfiles("local");
        assertThatCode(newGuard(environment, false, true, false, "*")::warnOnUnsafeDefaults)
                .doesNotThrowAnyException();
    }

    @Test
    void nonLocalProfileWithLooseDefaultsDoesNotThrowWhenStrictOff() {
        MockEnvironment environment = new MockEnvironment();
        environment.setActiveProfiles("dev");
        assertThatCode(newGuard(environment, false, true, false, "*")::warnOnUnsafeDefaults)
                .doesNotThrowAnyException();
    }

    @Test
    void prodStrictWithWildcardOriginsThrows() {
        MockEnvironment environment = new MockEnvironment();
        environment.setActiveProfiles("prod");
        assertThatThrownBy(newGuard(environment, true, true, false, "*")::warnOnUnsafeDefaults)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("allowed-origin-patterns");
    }

    @Test
    void prodStrictWithWildcardMixedInListThrows() {
        MockEnvironment environment = new MockEnvironment();
        environment.setActiveProfiles("prod");
        assertThatThrownBy(
                        newGuard(environment, true, true, false, "https://console.example.com, *")
                                ::warnOnUnsafeDefaults
                )
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("allowed-origin-patterns");
    }

    @Test
    void prodStrictWithRbacOffThrows() {
        MockEnvironment environment = new MockEnvironment();
        environment.setActiveProfiles("prod");
        assertThatThrownBy(newGuard(environment, true, false, false, "https://localhost")::warnOnUnsafeDefaults)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("rbac-enabled=false");
    }

    @Test
    void prodStrictWithRoleHeaderThrows() {
        MockEnvironment environment = new MockEnvironment();
        environment.setActiveProfiles("prod");
        assertThatThrownBy(newGuard(environment, true, true, true, "https://localhost")::warnOnUnsafeDefaults)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("local-role-header-enabled");
    }

    @Test
    void prodStrictWithHardenedConfigPasses() {
        MockEnvironment environment = new MockEnvironment();
        environment.setActiveProfiles("prod");
        assertThatCode(newGuard(environment, true, true, false, "https://localhost")::warnOnUnsafeDefaults)
                .doesNotThrowAnyException();
    }

    @Test
    void localIgnoresStrictFlag() {
        MockEnvironment environment = new MockEnvironment();
        environment.setActiveProfiles("local");
        assertThatCode(newGuard(environment, true, false, true, "*")::warnOnUnsafeDefaults)
                .doesNotThrowAnyException();
    }

    private static StartupSecurityGuard newGuard(
            MockEnvironment environment,
            boolean strictStartup,
            boolean rbacEnabled,
            boolean localRoleHeader,
            String origins
    ) {
        CommercialLicenseProperties license = new CommercialLicenseProperties();
        license.setEnforce(false);
        license.setRequireSignedBundles(false);
        WebSocketProperties websocket = new WebSocketProperties();
        websocket.setAllowedOriginPatterns(origins);
        IspfSecurityProperties security = new IspfSecurityProperties();
        security.setStrictStartup(strictStartup);
        security.setLocalRoleHeaderEnabled(localRoleHeader);
        security.setRbacEnabled(rbacEnabled);
        return new StartupSecurityGuard(environment, license, websocket, security);
    }
}
