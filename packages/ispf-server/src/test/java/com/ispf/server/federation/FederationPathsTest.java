package com.ispf.server.federation;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FederationPathsTest {

    @Test
    void buildsPeerCatalogRootFromName() {
        assertEquals(
                "root.platform.federation.site-a",
                FederationPaths.peerCatalogRoot("site-a")
        );
    }

    @Test
    void detectsCatalogMirrorPaths() {
        assertTrue(FederationPaths.isCatalogMirrorPath("root.platform.federation"));
        assertTrue(FederationPaths.isCatalogMirrorPath("root.platform.federation.site-a.devices.demo"));
        assertFalse(FederationPaths.isCatalogMirrorPath("root.platform.devices.demo-sensor-01"));
        assertFalse(FederationPaths.isCatalogMirrorPath(null));
    }
}
