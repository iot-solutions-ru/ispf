package com.ispf.server.bootstrap;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PlatformCatalogSortOrderTest {

    @Test
    void platformChildrenMatchProductionLayout() {
        assertEquals(
                PlatformCatalogSortOrder.platformChildren(),
                java.util.List.of(
                        "root.platform.security",
                        "root.platform.devices",
                        "root.platform.alert-rules",
                        "root.platform.operator-apps",
                        "root.platform.dashboards",
                        "root.platform.mimics",
                        "root.platform.relative-blueprints",
                        "root.platform.absolute-blueprints",
                        "root.platform.instance-types",
                        "root.platform.reports",
                        "root.platform.correlators",
                        "root.platform.workflows",
                        "root.platform.schedules",
                        "root.platform.data-sources",
                        "root.platform.bindings",
                        "root.platform.migrations",
                        "root.platform.federation",
                        "root.platform.applications",
                        "root.platform.instances"
                )
        );
    }

    @Test
    void assignsSequentialIndices() {
        for (int i = 0; i < PlatformCatalogSortOrder.platformChildren().size(); i++) {
            assertEquals(i, PlatformCatalogSortOrder.forPath(PlatformCatalogSortOrder.platformChildren().get(i)).orElse(-1));
        }
    }

    @Test
    void rootChildrenOrderPlatformBeforeTenants() {
        assertTrue(PlatformCatalogSortOrder.forPath("root.platform").orElse(-1)
                < PlatformCatalogSortOrder.forPath("root.tenant").orElse(-1));
    }
}
