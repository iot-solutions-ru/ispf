package com.ispf.server.federation;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FederationServiceTest {

    @Test
    void resolvesInboundBearerTokenWhenNoRequestContext() {
        assertTrue(FederationService.resolveInboundBearerToken().isEmpty());
    }

    @Test
    void resolvesRemotePathWithPrefix() {
        assertEquals(
                "root.platform.devices.demo-sensor-01",
                FederationService.resolveRemotePath("root.platform", "devices.demo-sensor-01")
        );
    }

    @Test
    void keepsPathWhenAlreadyPrefixed() {
        assertEquals(
                "root.platform.devices.demo-sensor-01",
                FederationService.resolveRemotePath("root.platform", "root.platform.devices.demo-sensor-01")
        );
    }
}
