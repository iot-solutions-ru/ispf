package com.ispf.server.bootstrap;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SystemObjectDescriptionsTest {

    @Test
    void resolvesExactPlatformPaths() {
        assertTrue(SystemObjectDescriptions.resolve("root.platform.federation").isPresent());
        assertTrue(SystemObjectDescriptions.resolve("root.platform.data-sources").isPresent());
    }

    @Test
    void resolvesMesCatalogPaths() {
        assertTrue(SystemObjectDescriptions.resolve("root.platform.mes").isPresent());
        assertTrue(SystemObjectDescriptions.resolve("root.platform.mes.work-orders").isPresent());
        assertTrue(SystemObjectDescriptions.resolve("root.platform.mes.instances").isPresent());
    }

    @Test
    void resolvesApplicationSubfolders() {
        assertTrue(SystemObjectDescriptions.resolve("root.platform.applications.demo.functions").isPresent());
        assertTrue(SystemObjectDescriptions.resolve("root.platform.applications.demo").isPresent());
        assertFalse(SystemObjectDescriptions.resolve("root.platform.applications.demo.extra.migrations").isPresent());
        assertFalse(SystemObjectDescriptions.resolve("root.platform.singleton-blueprints.migrations").isPresent());
    }

    @Test
    void resolvesFederationPeerRoot() {
        assertTrue(SystemObjectDescriptions.resolve("root.platform.federation.edge-site").isPresent());
        assertFalse(SystemObjectDescriptions.resolve("root.platform.federation.edge-site.devices.pump").isPresent());
    }
}
