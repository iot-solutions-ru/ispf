package com.ispf.server.config;

import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;

import static org.assertj.core.api.Assertions.assertThatCode;

class StartupSecurityGuardTest {

    @Test
    void relaxedLocalProfileDoesNotThrow() {
        MockEnvironment environment = new MockEnvironment();
        environment.setActiveProfiles("local");
        assertThatCode(newGuard(environment)::warnOnUnsafeDefaults).doesNotThrowAnyException();
    }

    @Test
    void nonLocalProfileWithLooseDefaultsDoesNotThrow() {
        MockEnvironment environment = new MockEnvironment();
        environment.setActiveProfiles("dev");
        assertThatCode(newGuard(environment)::warnOnUnsafeDefaults).doesNotThrowAnyException();
    }

    private static StartupSecurityGuard newGuard(MockEnvironment environment) {
        CommercialLicenseProperties license = new CommercialLicenseProperties();
        license.setEnforce(false);
        license.setRequireSignedBundles(false);
        WebSocketProperties websocket = new WebSocketProperties();
        websocket.setAllowedOriginPatterns("*");
        IspfSecurityProperties security = new IspfSecurityProperties();
        security.setLocalRoleHeaderEnabled(false);
        security.setRbacEnabled(true);
        return new StartupSecurityGuard(environment, license, websocket, security);
    }
}
